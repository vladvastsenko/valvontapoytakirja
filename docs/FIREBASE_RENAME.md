# Firebase setup
Register fi.vladi.tarkastuspro in the intended Firebase project. Download its google-services.json into app/ locally or provision it securely in CI. Configure SHA-1 and SHA-256 for signing certificates and Google sign-in. Do not reuse the old Cloud configuration. Firebase authentication code remains intact. Build is blocked until the matching configuration is supplied.
Storage uses a new BUILDING backup marker and remote sync/PDF directories to avoid importing the former universal Pro data.
