# Anfisa-CLI
Command Line Interface for Anfisa Backend

Anfisa provides REST API to its backend. The API is documented in https://github.com/ForomePlatform/anfisa/blob/master/app/REST.txt

This repository contains utilities that provide a Command Line Interface to this API

Examples of commands to create derived dataset:

    java -jar WS-creator-1.0.jar -c ../work/anfisa/a-setup/anfisa.json --parent xl_pgp3140_nist_4_4_2 -a apply --ds pgp3140_hearing_loss --rule "@Hearing Loss, v.5"
    java -jar WS-creator-1.0.jar -c ../work/anfisa/a-setup/anfisa.json --parent xl_pgp3140_nist_4_4_2 -a apply --ds pgp3140_hearing_loss --rule "@BGM Red Button"



