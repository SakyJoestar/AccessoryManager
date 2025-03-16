import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.FragmentLoginBinding
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var firebaseAuth: FirebaseAuth
    private val credentialManager by lazy { CredentialManager.create(requireContext()) }
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        switchDarkMode()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun enableDarkMode() {

    }

    private fun disableDarkMode() {

    }

    private fun switchDarkMode() {
        val swMode = binding.switchCompat
        if (binding.switchCompat.isChecked) {
            enableDarkMode()
        } else {
            disableDarkMode()
        }
    }


}
