# Safe Authenticator Android App

#### Setup CI
##### Env variables
- FIREBASE_PROJECT
- FIREBASE_APP_ID
- FIREBASE_GROUP
- FIREBASE_TOKEN

#### Distribute test version

- `./gradlew appDistributionLogin`
- `export FIREBASE_TOKEN=<token>`
- `./gradlew assembleDebug appDistributionUploadDebug`
- `./gradlew assembleMainnet appDistributionUploadMainnet`

