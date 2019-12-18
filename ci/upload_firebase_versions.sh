#!/bin/bash
# fail if any commands fails
set -e

if [[ $TRAVIS_BRANCH == 'master' ]] && [[ $TRAVIS_PULL_REQUEST == 'false' ]]
then
    ./gradlew assembleDebug appDistributionUploadDebug
    ./gradlew assembleMainnet appDistributionUploadMainnet
fi
