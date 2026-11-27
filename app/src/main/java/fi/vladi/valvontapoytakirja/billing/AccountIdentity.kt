package fi.vladi.valvontapoytakirja.billing

import com.google.firebase.auth.FirebaseUser

internal fun FirebaseUser?.hasPermanentIdentity(): Boolean =
    this?.providerData?.any { it.providerId == "password" || it.providerId == "google.com" } == true
