# OpenCode Mobile v2.0

A beautiful native Android client for [OpenCode](https://github.com/sst/opencode) - the AI coding agent.

## Features

- 🎨 **Modern UI** - Clean, dark theme inspired by OpenCode web
- 💬 **Session Management** - Create, switch, and manage multiple chat sessions
- 🤖 **Real-time Chat** - Stream responses with live updates via Server-Sent Events
- 🔌 **Connect to any OpenCode server** - Run `opencode serve` anywhere and connect
- 🎯 **Agent & Model Selection** - Choose from all configured providers and models
- ⚡ **Fast & Native** - Built with Jetpack Compose for smooth performance
- 🔒 **Secure** - Encrypted storage for API keys

## Quick Start

### On your computer
```bash
opencode serve --hostname 0.0.0.0 --port 4096
```

### On your Android device
1. Install the APK
2. Open the app and go to Settings (⚙️)
3. Enter your computer's IP address: `http://192.168.1.XXX:4096`
4. Tap Connect
5. Start chatting!

## Architecture

- **Kotlin + Jetpack Compose** - Modern Android UI
- **Retrofit + OkHttp** - API communication
- **Coroutines + Flow** - Reactive data streams
- **Server-Sent Events** - Real-time updates
- **Material 3** - Design system

## API Compatibility

Works with OpenCode v2 API including:
- Session management (`/session`)
- Messaging (`/session/:id/prompt`)
- Events streaming (`/event`)
- Agents, providers, models
- Tool calls and thinking steps

## Build

```bash
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`
