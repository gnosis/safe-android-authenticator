#!/bin/bash
# fail if any commands fails
set -e

if [[ $TRAVIS_BRANCH == 'master' ]]
then
    head -7 app/google-services.json
    ./gradlew assembleDebug appDistributionUploadDebug
    ./gradlew assembleMainnet appDistributionUploadMainnet
fi
