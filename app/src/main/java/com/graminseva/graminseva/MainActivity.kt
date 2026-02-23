package com.graminseva.graminseva

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.graminseva.graminseva.ui.theme.GraminSevaTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()

        setContent {
            GraminSevaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(auth)
                }
            }
        }
    }
}

/* ---------------- NAVIGATION ---------------- */

@Composable
fun AppNavigation(auth: FirebaseAuth) {

    val navController = rememberNavController()
    val db = FirebaseFirestore.getInstance()

    var verificationId by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("") }

    NavHost(navController, startDestination = "role") {

        composable("role") {
            RoleSelectionScreen {
                selectedRole = it
                navController.navigate("login")
            }
        }

        composable("login") {
            LoginScreen(auth) { id ->
                verificationId = id
                navController.navigate("otp")
            }
        }

        composable("otp") {
            OtpVerifyScreen(verificationId, auth) {

                val phone = auth.currentUser?.phoneNumber ?: return@OtpVerifyScreen

                if (selectedRole == "User") {

                    db.collection("users").document(phone).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                navController.navigate("services")
                            } else {
                                navController.navigate("register")
                            }
                        }

                } else {

                    db.collection("workers").document(phone).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                navController.navigate("worker_dashboard")
                            } else {
                                navController.navigate("register")
                            }
                        }
                }
            }
        }

        composable("register") {
            RegistrationScreen(selectedRole, auth, navController)
        }

        composable("services") {
            ServiceListScreen {
                navController.navigate("workers/$it")
            }
        }

        composable("worker_dashboard") {
            WorkerDashboardScreen()
        }

        composable("workers/{service}") {
            val service = it.arguments?.getString("service") ?: ""
            WorkerListScreen(service, auth)
        }
    }
}

/* ---------------- ROLE ---------------- */

@Composable
fun RoleSelectionScreen(onSelect: (String) -> Unit) {

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Gramin Seva", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Button(onClick = { onSelect("User") }, modifier = Modifier.fillMaxWidth()) {
            Text("I Need a Service")
        }

        Spacer(Modifier.height(12.dp))

        Button(onClick = { onSelect("Worker") }, modifier = Modifier.fillMaxWidth()) {
            Text("I Provide a Service")
        }
    }
}

/* ---------------- LOGIN ---------------- */

@Composable
fun LoginScreen(auth: FirebaseAuth, onCodeSent: (String) -> Unit) {

    var phone by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(24.dp)) {

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone (+91XXXXXXXXXX)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = {

            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(context as MainActivity)
                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        auth.signInWithCredential(credential)
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                    }

                    override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                        onCodeSent(id)
                    }
                }).build()

            PhoneAuthProvider.verifyPhoneNumber(options)

        }) {
            Text("Send OTP")
        }
    }
}

/* ---------------- OTP ---------------- */

@Composable
fun OtpVerifyScreen(verificationId: String, auth: FirebaseAuth, onSuccess: () -> Unit) {

    var otp by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(24.dp)) {

        OutlinedTextField(
            value = otp,
            onValueChange = { otp = it },
            label = { Text("Enter OTP") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = {

            val credential = PhoneAuthProvider.getCredential(verificationId, otp)

            auth.signInWithCredential(credential)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener {
                    Toast.makeText(context, "Invalid OTP", Toast.LENGTH_SHORT).show()
                }

        }) {
            Text("Verify")
        }
    }
}

/* ---------------- REGISTRATION ---------------- */

@Composable
fun RegistrationScreen(role: String, auth: FirebaseAuth, navController: NavHostController) {

    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var village by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var profession by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var available by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {

        Text("Register as $role", fontSize = 20.sp)

        OutlinedTextField(name, { name = it }, label = { Text("Name") })
        OutlinedTextField(village, { village = it }, label = { Text("Village") })
        OutlinedTextField(district, { district = it }, label = { Text("District") })

        if (role == "Worker") {
            OutlinedTextField(profession, { profession = it }, label = { Text("Profession") })
            OutlinedTextField(experience, { experience = it }, label = { Text("Experience") })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(available, { available = it })
                Text(if (available) "Available" else "Not Available",
                    color = if (available) Color.Green else Color.Red)
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = {

            val phone = auth.currentUser?.phoneNumber ?: return@Button

            if (role == "User") {

                val userData = hashMapOf(
                    "name" to name,
                    "village" to village.lowercase(),
                    "district" to district.lowercase(),
                    "phone" to phone
                )

                db.collection("users").document(phone).set(userData)
                    .addOnSuccessListener {
                        navController.navigate("services")
                    }

            } else {

                val workerData = hashMapOf(
                    "name" to name,
                    "village" to village.lowercase(),
                    "district" to district.lowercase(),
                    "profession" to profession.lowercase(),
                    "experience" to experience,
                    "available" to available,
                    "phone" to phone
                )

                db.collection("workers").document(phone).set(workerData)
                    .addOnSuccessListener {
                        navController.navigate("worker_dashboard")
                    }
            }

        }) {
            Text("Finish Registration")
        }
    }
}

/* ---------------- WORKER LIST ---------------- */

@Composable
fun WorkerListScreen(service: String, auth: FirebaseAuth) {

    val db = FirebaseFirestore.getInstance()
    val workers = remember { mutableStateListOf<Map<String, Any>>() }

    LaunchedEffect(true) {

        val phone = auth.currentUser?.phoneNumber ?: return@LaunchedEffect

        db.collection("users").document(phone).get()
            .addOnSuccessListener { userDoc ->

                val village = userDoc.getString("village") ?: ""

                db.collection("workers")
                    .whereEqualTo("profession", service)
                    .whereEqualTo("village", village)
                    .whereEqualTo("available", true)
                    .get()
                    .addOnSuccessListener { result ->
                        workers.clear()
                        for (doc in result) workers.add(doc.data)
                    }
            }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        items(workers) { worker ->
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(worker["name"].toString(), fontWeight = FontWeight.Bold)
                    Text("Experience: ${worker["experience"]} years")
                    Text("Phone: ${worker["phone"]}")
                }
            }
        }
    }
}

/* ---------------- WORKER DASHBOARD ---------------- */

@Composable
fun WorkerDashboardScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Worker Dashboard", fontSize = 22.sp)
    }
}
@Composable
fun ServiceListScreen(onSelect: (String) -> Unit) {

    val services = listOf("plumber", "electrician", "carpenter", "mechanic")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(services) { service ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable { onSelect(service) }
            ) {
                Text(
                    text = service.uppercase(),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
