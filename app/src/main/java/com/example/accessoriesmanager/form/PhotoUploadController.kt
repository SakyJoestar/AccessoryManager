package com.example.accessoriesmanager.form

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.app.Dialog
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.accessoriesmanager.R
import com.example.accessoriesmanager.adapter.InstallationPhotosPagerAdapter
import com.example.accessoriesmanager.adapter.PhotoFullscreenPagerAdapter
import com.example.accessoriesmanager.ui.showSnack
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.io.File
import android.view.ViewGroup
import android.widget.ImageButton

private const val MAX_PHOTOS = 5

/**
 * Owns photo capture/selection state for a single installation form: the camera/gallery
 * launchers (which must be registered before the fragment reaches CREATED, hence the
 * constructor-time registration) and the carousel UI they feed.
 */
class PhotoUploadController(private val fragment: Fragment) {

    val selectedPhotoUris = mutableListOf<Uri>()
    private var tempCameraUri: Uri? = null
    private var hasSeededExisting = false

    private lateinit var cardAddPhoto: View
    private lateinit var tvPhotosCounter: TextView
    private lateinit var layoutPhotosCarousel: View
    private lateinit var vpPhotos: ViewPager2
    private lateinit var tabPhotosIndicator: TabLayout
    private lateinit var fabAddMorePhotos: FloatingActionButton

    private lateinit var photosPagerAdapter: InstallationPhotosPagerAdapter
    private var photosTabMediator: TabLayoutMediator? = null
    private var photoTabsListener: TabLayout.OnTabSelectedListener? = null

    private val pickMultipleMedia =
        fragment.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
            if (uris.isNullOrEmpty()) return@registerForActivityResult

            val available = MAX_PHOTOS - selectedPhotoUris.size
            if (available <= 0) {
                fragment.showSnack("Máximo $MAX_PHOTOS fotos")
                return@registerForActivityResult
            }

            selectedPhotoUris.addAll(uris.take(available))
            updatePhotosUi()
        }

    private val takePicture =
        fragment.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                tempCameraUri?.let { uri ->
                    if (selectedPhotoUris.size >= MAX_PHOTOS) {
                        fragment.showSnack("Máximo $MAX_PHOTOS fotos")
                    } else {
                        selectedPhotoUris.add(uri)
                        updatePhotosUi()
                    }
                }
            }
        }

    private val requestCameraPermission =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openCamera()
            } else {
                fragment.showSnack("Se necesita permiso de cámara para tomar fotos")
            }
        }

    /** Call from onViewCreated with the freshly-inflated views. */
    fun bindViews(
        cardAddPhoto: View,
        tvPhotosCounter: TextView,
        layoutPhotosCarousel: View,
        vpPhotos: ViewPager2,
        tabPhotosIndicator: TabLayout,
        fabAddMorePhotos: FloatingActionButton,
    ) {
        this.cardAddPhoto = cardAddPhoto
        this.tvPhotosCounter = tvPhotosCounter
        this.layoutPhotosCarousel = layoutPhotosCarousel
        this.vpPhotos = vpPhotos
        this.tabPhotosIndicator = tabPhotosIndicator
        this.fabAddMorePhotos = fabAddMorePhotos

        photosPagerAdapter = InstallationPhotosPagerAdapter(
            items = mutableListOf(),
            onPhotoClick = { uri -> showFullscreenViewer(selectedPhotoUris.indexOf(uri)) },
            onRemoveClick = { position, uri ->
                selectedPhotoUris.remove(uri)
                updatePhotosUi()

                if (selectedPhotoUris.isNotEmpty()) {
                    val safePosition = position.coerceAtMost(selectedPhotoUris.lastIndex)
                    vpPhotos.setCurrentItem(safePosition, false)
                }
            }
        )
        vpPhotos.adapter = photosPagerAdapter

        cardAddPhoto.setOnClickListener { showPhotoOptions() }
        fabAddMorePhotos.setOnClickListener { showPhotoOptions() }
    }

    /** Call from onDestroyView to release listeners tied to the (soon-to-be-gone) views. */
    fun unbindViews() {
        photoTabsListener?.let { tabPhotosIndicator.removeOnTabSelectedListener(it) }
        photoTabsListener = null

        photosTabMediator?.detach()
        photosTabMediator = null
    }

    /** Clears all pending local photos and resets the carousel UI (e.g. after a successful save). */
    fun clear() {
        selectedPhotoUris.clear()
        updatePhotosUi()
    }

    /**
     * Seeds the carousel with an existing installation's already-uploaded photo [urls] (edit mode).
     * A no-op past the first call so re-collecting the form's StateFlow (e.g. after the fragment
     * goes through STOPPED/STARTED while a picker activity is in front) doesn't wipe local edits.
     */
    fun seedExistingPhotos(urls: List<String>) {
        if (hasSeededExisting) return
        hasSeededExisting = true
        if (urls.isEmpty()) return

        val available = MAX_PHOTOS - selectedPhotoUris.size
        if (available <= 0) return

        selectedPhotoUris.addAll(urls.take(available).map(Uri::parse))
        updatePhotosUi()
    }

    private fun Uri.isRemote() = scheme == "http" || scheme == "https"

    /** Local (not-yet-uploaded) photo uris to send to Cloudinary on save. */
    fun pendingLocalUris(): List<Uri> = selectedPhotoUris.filterNot { it.isRemote() }

    /** Already-uploaded photo urls the user kept (i.e. didn't remove) in this editing session. */
    fun keptExistingPhotoUrls(): List<String> =
        selectedPhotoUris.filter { it.isRemote() }.map { it.toString() }

    fun showPhotoOptions() {
        val dialogView = fragment.layoutInflater.inflate(R.layout.dialog_photo_options, null)

        val optionCamera = dialogView.findViewById<View>(R.id.optionCamera)
        val optionGallery = dialogView.findViewById<View>(R.id.optionGallery)

        val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setView(dialogView)
            .create()

        optionCamera.setOnClickListener {
            dialog.dismiss()
            openCamera()
        }

        optionGallery.setOnClickListener {
            dialog.dismiss()
            openGallery()
        }

        dialog.show()
    }

    /** Full-screen swipeable viewer over [selectedPhotoUris], starting at [startPosition]. */
    private fun showFullscreenViewer(startPosition: Int) {
        if (selectedPhotoUris.isEmpty()) return

        val dialog = Dialog(fragment.requireContext(), R.style.Theme_PhotoFullscreenDialog)
        val view = fragment.layoutInflater.inflate(R.layout.dialog_photo_fullscreen, null)
        dialog.setContentView(view)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val vpFullscreen = view.findViewById<ViewPager2>(R.id.vpFullscreenPhotos)
        val tabFullscreenIndicator = view.findViewById<TabLayout>(R.id.tabFullscreenIndicator)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseFullscreen)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteFullscreen)

        val fullscreenAdapter = PhotoFullscreenPagerAdapter(selectedPhotoUris.toMutableList())
        vpFullscreen.adapter = fullscreenAdapter
        vpFullscreen.setCurrentItem(startPosition.coerceIn(0, selectedPhotoUris.lastIndex), false)

        var mediator: TabLayoutMediator? = TabLayoutMediator(tabFullscreenIndicator, vpFullscreen) { _, _ -> }
        mediator?.attach()

        fun refreshDots() {
            for (i in 0 until tabFullscreenIndicator.tabCount) {
                tabFullscreenIndicator.getTabAt(i)?.setIcon(R.drawable.dot_indicator_unselected)
            }
            tabFullscreenIndicator.getTabAt(vpFullscreen.currentItem)
                ?.setIcon(R.drawable.dot_indicator_selected)
        }

        val tabListener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tab.setIcon(R.drawable.dot_indicator_selected)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {
                tab.setIcon(R.drawable.dot_indicator_unselected)
            }
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        }
        tabFullscreenIndicator.addOnTabSelectedListener(tabListener)
        refreshDots()

        dialog.setOnDismissListener {
            tabFullscreenIndicator.removeOnTabSelectedListener(tabListener)
            mediator?.detach()
            mediator = null
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        btnDelete.setOnClickListener {
            val position = vpFullscreen.currentItem
            val uri = fullscreenAdapter.removeAt(position) ?: return@setOnClickListener
            selectedPhotoUris.remove(uri)
            updatePhotosUi()

            if (fullscreenAdapter.itemCount == 0) {
                dialog.dismiss()
            } else {
                vpFullscreen.setCurrentItem(position.coerceAtMost(fullscreenAdapter.itemCount - 1), false)
                refreshDots()
            }
        }

        dialog.show()
    }

    private fun openGallery() {
        val available = MAX_PHOTOS - selectedPhotoUris.size
        if (available <= 0) {
            fragment.showSnack("Máximo $MAX_PHOTOS fotos")
            return
        }

        pickMultipleMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun openCamera() {
        val context = fragment.requireContext()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
            return
        }

        val photoFile = File.createTempFile("installation_photo_", ".jpg", context.cacheDir)

        tempCameraUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            photoFile
        )

        takePicture.launch(tempCameraUri)
    }

    private fun updatePhotosUi() {
        val count = selectedPhotoUris.size
        tvPhotosCounter.text = "$count/$MAX_PHOTOS"

        val hasPhotos = count > 0

        cardAddPhoto.visibility = if (hasPhotos) View.GONE else View.VISIBLE
        layoutPhotosCarousel.visibility = if (hasPhotos) View.VISIBLE else View.GONE
        tabPhotosIndicator.visibility = if (hasPhotos) View.VISIBLE else View.GONE
        fabAddMorePhotos.visibility = if (count in 1 until MAX_PHOTOS) View.VISIBLE else View.GONE

        photosPagerAdapter.submitItems(selectedPhotoUris.toList())

        photosTabMediator?.detach()
        photosTabMediator = null

        if (hasPhotos) {
            photosTabMediator = TabLayoutMediator(tabPhotosIndicator, vpPhotos) { _, _ -> }
            photosTabMediator?.attach()
            setupPhotoDots()
        }
    }

    private fun setupPhotoDots() {
        for (i in 0 until tabPhotosIndicator.tabCount) {
            tabPhotosIndicator.getTabAt(i)?.setIcon(R.drawable.dot_indicator_unselected)
        }

        tabPhotosIndicator.getTabAt(vpPhotos.currentItem)
            ?.setIcon(R.drawable.dot_indicator_selected)

        photoTabsListener?.let { tabPhotosIndicator.removeOnTabSelectedListener(it) }

        photoTabsListener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tab.setIcon(R.drawable.dot_indicator_selected)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                tab.setIcon(R.drawable.dot_indicator_unselected)
            }

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        }

        tabPhotosIndicator.addOnTabSelectedListener(photoTabsListener!!)
    }
}
