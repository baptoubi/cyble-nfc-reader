# Lecteur Cyble 5

Petite appli Android qui lit en NFC (ISO 15693) la mémoire d'un compteur Itron Cyble 5 et affiche
identifiant, index, alarmes commentées et réglages de communication.
Le dump lu peut être copié ou partagé au format « [ 42:20:00:00 ] Addr. 08 : DATA », lisible par la page web Décodeur Cyble 5.

## Compiler l'APK

### Option A : sans rien installer (GitHub Actions)
1. Crée un dépôt GitHub privé et envoie-y le contenu de ce dossier (y compris `.github/`).
2. Onglet Actions, workflow « Build APK », bouton Run workflow.
3. Télécharge l'artefact `lecteur-cyble5-apk` : il contient `app-debug.apk`.

### Option B : Android Studio
Ouvre ce dossier, laisse Gradle se synchroniser, puis Build > Build APK(s).
Le fichier est dans `app/build/outputs/apk/debug/app-debug.apk`.

## Installer
Copie l'APK sur le téléphone et ouvre-le (autorise l'installation depuis cette source si Android le demande).
Active le NFC, ouvre l'appli, pose le dos du téléphone sur la zone NFC du compteur et ne bouge plus pendant la lecture.

## Fichiers
- `Decoder.java` : décodage des blocs (adresses d'octet relevées dans l'appli Itron NFC.FieldTool).
- `Report.java` : texte affiché et export du dump.
- `TagReader.java` : lecture ISO 15693 par blocs de 4 octets (READ_SINGLE_BLOCK / READ_MULTIPLE_BLOCKS).
- `MainActivity.java` : écran.
