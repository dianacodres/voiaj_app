package com.example.mobile_app;


import android.content.Context;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralizeaza toata logica de autentificare (email + Google) si scrierea/citirea
 * documentului users/{uid} din Firestore. Activity-urile si ViewModel-urile
 * nu vorbesc direct cu FirebaseAuth/Firestore, ci doar cu aceasta clasa.
 */
public class AuthRepository {

    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    public AuthRepository() {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
    }

    // ===================== CALLBACK-URI =====================

    public interface AuthCallback {
        void onSuccess(User user);
        void onError(String errorMessage);
    }

    public interface GoogleSignInCallback {
        void onExistingUser(User user);
        void onNewUserNeedsInterests(User partialUser); // trebuie completat cu interese si salvat
        void onError(String errorMessage);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    // ===================== SIGN UP CU EMAIL =====================

    /**
     * Foloseste pentru ecranul "Create your account" (Name, Email, Password + interese).
     */
    public void signUpWithEmail(String name, String email, String password,
                                List<String> interests, AuthCallback callback) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError("Contul nu a putut fi creat.");
                        return;
                    }
                    User newUser = new User(name, email, "email", interests);
                    newUser.setUid(firebaseUser.getUid());

                    firestore.collection("users")
                            .document(firebaseUser.getUid())
                            .set(newUser)
                            .addOnSuccessListener(unused -> callback.onSuccess(newUser))
                            .addOnFailureListener(e -> callback.onError(
                                    "Contul a fost creat, dar profilul nu a putut fi salvat: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ===================== LOGIN CU EMAIL =====================

    /**
     * Foloseste pentru ecranul "Welcome back" (Email, Password, Log in).
     */
    public void loginWithEmail(String email, String password, AuthCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError("Autentificare esuata.");
                        return;
                    }
                    fetchUserDocument(firebaseUser.getUid(), callback);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ===================== GOOGLE SIGN-IN =====================

    /**
     * Configureaza clientul Google Sign-In. Se apeleaza in Activity, la butonul "Google",
     * si se lanseaza cu ActivityResultLauncher (nu cu startActivityForResult, e deprecated).
     */
    public GoogleSignInClient getGoogleSignInClient(Context context) {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        return GoogleSignIn.getClient(context, options);
    }

    /**
     * Se apeleaza DUPA ce GoogleSignInAccount a fost obtinut cu succes in Activity
     * (din account.getIdToken(), account.getDisplayName(), account.getEmail()).
     *
     * Verifica daca userul exista deja in Firestore:
     *  - daca da -> onExistingUser, mergi direct la Discover
     *  - daca nu -> onNewUserNeedsInterests, trimite-l la ecranul de selectare interese
     */
    public void firebaseAuthWithGoogle(String idToken, String displayName, String email,
                                       GoogleSignInCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError("Autentificare Google esuata.");
                        return;
                    }
                    String uid = firebaseUser.getUid();

                    firestore.collection("users").document(uid).get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    User existingUser = documentSnapshot.toObject(User.class);
                                    callback.onExistingUser(existingUser);
                                } else {
                                    User newUser = new User(displayName, email, "google", new ArrayList<>());
                                    newUser.setUid(uid);
                                    callback.onNewUserNeedsInterests(newUser);
                                }
                            })
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Se apeleaza dupa ce un user NOU venit prin Google a ales interesele
     * (prima logare -> Google nu ne da interese, trebuie cerute separat).
     */
    public void saveNewGoogleUser(User user, List<String> interests, AuthCallback callback) {
        user.setInterests(interests);
        firestore.collection("users").document(user.getUid())
                .set(user)
                .addOnSuccessListener(unused -> callback.onSuccess(user))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Foloseste pentru ecranul de profil, ca sa incarci datele userului logat curent
     * (nume, email, interese) direct din Firestore la deschiderea ecranului.
     */
    public void getCurrentUserProfile(AuthCallback callback) {
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onError("Niciun utilizator autentificat.");
            return;
        }
        fetchUserDocument(firebaseUser.getUid(), callback);
    }

    /**
     * Schimba parola userului curent. Firebase cere re-autentificare cu parola veche
     * inainte sa permita schimbarea - nu poti apela direct updatePassword fara asta,
     * altfel primesti eroare "requires-recent-login".
     * Nu se aplica pentru conturi Google (authProvider = "google"), acelea nu au parola la noi.
     */
    public void changePassword(String currentPassword, String newPassword, SimpleCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            callback.onError("Niciun utilizator autentificat.");
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential)
                .addOnSuccessListener(unused -> user.updatePassword(newPassword)
                        .addOnSuccessListener(unused2 -> callback.onSuccess())
                        .addOnFailureListener(e -> callback.onError(e.getMessage())))
                .addOnFailureListener(e -> callback.onError("Parola curenta este incorecta."));
    }

    // ===================== PROFIL =====================

    /**
     * Foloseste pentru "Change Interests" din ecranul de profil.
     */
    public void updateInterests(String uid, List<String> interests, AuthCallback callback) {
        firestore.collection("users").document(uid)
                .update("interests", interests)
                .addOnSuccessListener(unused -> fetchUserDocument(uid, callback))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Foloseste pentru "Edit Profile" - actualizeaza DOAR numele in Firestore.
     * Emailul NU se schimba de aici: modificarea emailului real necesita
     * re-autentificare + verifyBeforeUpdateEmail, e un flux separat, mai sensibil.
     */
    public void updateName(String uid, String newName, AuthCallback callback) {
        firestore.collection("users").document(uid)
                .update("name", newName, "avatarInitials", User.computeInitials(newName))
                .addOnSuccessListener(unused -> fetchUserDocument(uid, callback))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ===================== HELPERS =====================

    private void fetchUserDocument(String uid, AuthCallback callback) {
        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        callback.onSuccess(user);
                    } else {
                        callback.onError("Profilul utilizatorului nu a fost gasit in Firestore.");
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void signOut() {
        firebaseAuth.signOut();
    }

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }
}
