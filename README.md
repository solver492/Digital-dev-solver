# OpenCode Mobile

Client Android **natif** (Kotlin + Jetpack Compose) pour [OpenCode](https://opencode.ai).
Il se connecte en Wi-Fi/LAN (ou via tunnel) à un serveur `opencode serve` et donne accès à
**toutes** les capacités d'OpenCode depuis le téléphone : sessions, agents, modèles
(locaux, cloud et gratuits), outils, raisonnement, compaction de contexte.

## Fonctionnalités

- **Sessions** : liste, recherche, création, suppression, reprise de n'importe quelle session du serveur.
- **Chat complet** : rendu Markdown (blocs de code + bouton copier), raisonnement repliable,
  appels d'outils avec statut (en attente / en cours / terminé / erreur) et sortie dépliable,
  compactage de contexte, erreurs du modèle affichées proprement.
- **Choix du modèle** : feuille de sélection avec recherche, regroupement par fournisseur,
  badges `GRATUIT`, `RAISONNEMENT`, `OUTILS`, appliqué à la session via l'API.
- **Choix de l'agent** : liste des agents exposés par le serveur, valeur par défaut incluse.
- **Streaming par polling** : le client interroge `/api/session/{id}/message` et
  `/api/session/active` — robuste en veille, en cas de Wi-Fi instable ou de redémarrage du serveur.
- **Interruption** : bouton stop qui appelle `POST /api/session/{id}/interrupt`.
- **Connexion sécurisée** : HTTP Basic (`opencode` + mot de passe du serveur), mot de passe
  stocké chiffré (`EncryptedSharedPreferences`), reconnexion automatique au démarrage.
- **Actions rapides** personnalisables sur l'écran d'accueil.

## Démarrage

1. Sur l'ordinateur :

   ```bash
   opencode serve --hostname 0.0.0.0 --port 4096
   ```

   Notez la ligne affichée : `server password <token>`.

2. Installez l'APK (artifact CI ou `./gradlew assembleDebug`).
3. Dans l'app → **Connexion** : adresse `http://<ip-locale-de-l-ordi>:4096` + le mot de passe.
4. C'est tout : vos sessions existantes apparaissent.

> Le username Basic est toujours `opencode` ; seul le mot de passe change à chaque lancement
> de `opencode serve`.

## Architecture

```
app/src/main/java/com/digitalsolverland/opencode/
├── OpenCodeApp.kt                  navigation + snackbars
├── MainActivity.kt
├── SecureSettings.kt               URL, mot de passe, modèle/agent par défaut (chiffrés)
├── data/
│   ├── api/ApiClient.kt            Retrofit dynamique + Basic auth
│   ├── api/OpenCodeApi.kt          endpoints /api/... (v2)
│   ├── model/Models.kt             sessions, messages, modèles, agents
│   └── repo/OpenCodeRepository.kt  état + polling de fin de tour
├── viewmodel/OpenCodeViewModel.kt  état UI, sélection modèle/agent
└── ui/
    ├── components/MarkdownText.kt  rendu Markdown/code
    ├── screens/SessionsScreen.kt
    ├── screens/ChatScreen.kt
    ├── screens/SettingsScreen.kt
    └── theme/
```

L'ancien service d'exécution d'un Node.js embarqué (`OpenCodeService`, `AgentBus`, `BootReceiver`)
est conservé mais **n'est pas requis** pour utiliser l'app : le client parle directement à
`opencode serve`.

## Build

L'APK est construit automatiquement par GitHub Actions à chaque push sur `main`
(workflow `.github/workflows/build-apk.yml`) et publié comme artifact
`OpenCode-Mobile-Debug-APK`.

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
