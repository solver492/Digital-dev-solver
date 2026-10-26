# OpenCode Mobile

Client Android **natif** (Kotlin + Jetpack Compose) pour [OpenCode](https://opencode.ai), avec
**trois modes d'utilisation** :

| Mode | Ce qu'il faut | Idéal pour |
| --- | --- | --- |
| **Sur le téléphone** (autonome) | rien d'autre que l'app | travailler sans PC |
| **PC en Wi-Fi** | `opencode serve` sur l'ordinateur | utiliser la puissance du PC, le même Wi-Fi |
| **Serveur distant** | une URL publique + mot de passe | accès depuis n'importe où |

Le mode autonome installe une **mini-distribution Debian 12 arm64** dans le stockage privé de
l'application, puis lance le **vrai serveur OpenCode 2.1.4** à l'intérieur via **proot**.
Aucun root, aucune modification du système, aucun PC.

## Fonctionnalités

- **Sessions** : liste, recherche, création, suppression, reprise de n'importe quelle session du serveur.
- **Chat complet** : rendu Markdown (blocs de code + bouton copier), raisonnement repliable,
  appels d'outils avec statut et sortie dépliable, compactage de contexte.
- **Choix du modèle** : recherche, regroupement par fournisseur, badges `GRATUIT`,
  `RAISONNEMENT`, `OUTILS`, appliqué à la session via l'API.
- **Choix de l'agent** : liste des agents exposés par le serveur.
- **Streaming par polling** : robuste en veille et en cas de Wi-Fi instable.
- **Interruption**, **reconnexion automatique**, **mot de passe chiffré**
  (`EncryptedSharedPreferences`).
- **Serveur local** : installation du runtime, démarrage/arrêt, état, port, health-check.
- **Journal** : toutes les sorties du serveur proot, copiables en un tap (pour le support).
- **Terminal** : console Debian embarquée (commandes non interactives : `ls`, `apt`, `git`, …).

## Démarrage — mode autonome (recommandé)

1. Installer l'APK (artifact CI ou `./gradlew assembleDebug`).
2. Ouvrir l'app → **« Utiliser ce téléphone (autonome) »**.
3. **Installer le runtime Linux** : téléchargement unique d'environ 180 Mo, puis extraction
   (~500 Mo d'espace libre nécessaires). Wi-Fi recommandé.
4. **Démarrer le serveur** : le premier lancement prend quelques secondes.
5. **Utiliser ce serveur** : l'app se connecte à `http://127.0.0.1:4096` et vos sessions
   OpenCode apparaissent.

Le serveur écoute uniquement sur `127.0.0.1` : il n'est pas exposé au réseau. Il tourne dans un
service de premier plan (notification + wake-lock) et peut démarrer automatiquement au boot.

> Le runtime est publié comme asset de release : `runtime-v1`
> (`opencode-runtime-arm64-v1.tar.gz`, SHA-256 vérifié par l'app).

## Démarrage — PC en Wi-Fi / serveur distant

1. Sur l'ordinateur :

   ```bash
   opencode serve --hostname 0.0.0.0 --port 4096
   ```

   Notez la ligne `server password <token>`.

2. Dans l'app → **Connexion** : adresse `http://<ip-locale>:4096` + le mot de passe.
3. C'est tout : vos sessions existantes apparaissent.

> Le username Basic est toujours `opencode` ; seul le mot de passe change à chaque lancement.

## Architecture

```
app/src/main/
├── assets/proot/                   proot + loader + libtalloc/libandroid-shmem (bionic)
└── java/com/digitalsolverland/opencode/
    ├── OpenCodeApp.kt              navigation + snackbars
    ├── LocalServerService.kt       service de premier plan du serveur embarqué
    ├── BootReceiver.kt             démarrage auto au boot (option)
    ├── SecureSettings.kt           URL, mots de passe, modèle/agent (chiffrés)
    ├── runtime/
    │   ├── BundleInstaller.kt      téléchargement + SHA-256 + installation
    │   ├── TarGzExtractor.kt       extraction tar.gz (liens, permissions)
    │   ├── RuntimePaths.kt         emplacements du rootfs
    │   └── LocalServer.kt          proot, health-check, exec, états
    ├── data/                       Retrofit + modèles + repository (API v2)
    ├── viewmodel/OpenCodeViewModel.kt
    └── ui/
        ├── screens/SessionsScreen.kt
        ├── screens/ChatScreen.kt
        ├── screens/SettingsScreen.kt
        ├── screens/LocalServerScreen.kt
        ├── screens/LogsScreen.kt
        └── screens/TerminalScreen.kt
```

### Fonctionnement du mode autonome

```
App Android (uid app, targetSdk 28)
  └── proot (bionic, RPATH → files/runtime/lib)
        └── Debian 12 arm64 (files/runtime/rootfs)
              └── /usr/local/bin/opencode serve --hostname 127.0.0.1 --port 4096
```

- `targetSdk 28` est volontaire : comme Termux, il autorise l'exécution de binaires présents
  dans le stockage de l'app (la règle W^X d'Android 10+ l'interdit à partir de `targetSdk 29`).
- `proot` est un binaire bionic (paquet Termux) dont le `RPATH` a été réécrit vers
  `files/runtime/lib` ; le loader est fourni via `PROOT_LOADER`.
- Le rootfs est monté par proot avec `/dev`, `/proc`, `/sys`, `/tmp` et le dossier partagé
  `files/workspace` (visible dans la Debian sous `/root/workspace`).

## Build

L'APK est construit automatiquement par GitHub Actions à chaque push sur `main`
(`.github/workflows/build-apk.yml`) et publié comme artifact `OpenCode-Mobile-Debug-APK`.

En local :

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Prérequis : JDK 17, Android SDK 35.

## API utilisée (OpenCode v2)

| Méthode | Route | Rôle |
| --- | --- | --- |
| `GET` | `/api/session` | liste des sessions |
| `POST` | `/api/session` | création |
| `DELETE` | `/api/session/{id}` | suppression |
| `GET` | `/api/session/{id}/message` | historique / polling |
| `POST` | `/api/session/{id}/prompt` | envoi d'un prompt |
| `POST` | `/api/session/{id}/interrupt` | stop |
| `POST` | `/api/session/{id}/model` | changement de modèle |
| `POST` | `/api/session/{id}/agent` | changement d'agent |
| `GET` | `/api/session/active` | statut d'exécution |
| `GET` | `/api/model`, `/api/agent` | catalogues |

## Signature stable des APK

Les APK sont signés avec la clé `app/keystore/opencode-release.jks`
(alias `opencode`, mot de passe `opencode-mobile`). Elle est versionnée
volontairement : sans elle, chaque build CI générait une clé de debug
différente et Android refusait la mise à jour (« Application non installée »).

Grâce à cette clé, toute nouvelle version s'installe **par-dessus** la
précédente, sans désinstallation ni perte de réglages.
