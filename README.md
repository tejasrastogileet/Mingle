# Mingle — Video Conferencing Platform

**Meet. Connect. Mingle.**

A full-stack video conferencing web application built with **Spring Boot**, **MongoDB**, **WebSockets**, **AgoraRTC**, **React**, **Vite**, and **Tailwind CSS**.

---

## 🚀 Features

- ✅ User Authentication (Register/Login)
- 📅 Create & Join Meeting Rooms
- 🎥 Real-time Video & Audio Communication (via AgoraRTC)
- 💬 Live Chat within meetings (WebSocket powered)
- 👥 Participant Management
- 📝 Meeting History
- 🔐 Secure JWT-based Authorization
- 📱 Fully Responsive UI

---

## 🛠️ Tech Stack

### 💻 Backend:
- Spring Boot
- MongoDB
- Spring Data MongoDB
- WebSockets (STOMP over SockJS)
- Spring Security + JWT
- RESTful APIs

### 🌐 Frontend:
- React (with Vite)
- Tailwind CSS
- AgoraRTC SDK
- Axios, React Router
- WebSocket Client (SockJS + STOMP)

---

## ⚙️ Getting Started

### 🔧 Prerequisites

- Java 17+
- Node.js 18+
- MongoDB Atlas cluster (or a local MongoDB server)
- Agora Developer Account

---

### 🧪 Backend Setup

1. Copy `Backend/meetnow/.env.example` to `Backend/meetnow/.env` and set `MONGODB_URI` to your MongoDB Atlas connection string. The `.env` file is ignored by Git. URL-encode special characters in the database user's password.

   From the repository root, create the local file:

   ```powershell
   Copy-Item Backend\meetnow\.env.example Backend\meetnow\.env
   ```

   For local JWT signing, copy the local-only example configuration as well:

   ```powershell
   Copy-Item Backend\meetnow\application-local.properties.example Backend\meetnow\application-local.properties
   ```

   This development key must not be used in a deployed environment. Production deployments must set a unique `JWT_SECRET_KEY`.

   For example:

   ```env
   MONGODB_URI=mongodb+srv://<db_username>:<url_encoded_password>@<cluster-host>/meetnow_db?retryWrites=true&w=majority
   ```

2. From the repository root, run the backend. The VS Code launch configuration sets the working directory to `Backend/meetnow` so Spring Boot finds the local `.env` file.

   PowerShell:

   ```powershell
   cd Backend\meetnow
   .\mvnw.cmd spring-boot:run
   ```

   Git Bash:

   ```bash
   cd Backend/meetnow
   ./mvnw spring-boot:run
   ```

   Alternatively, run `mvn spring-boot:run` from `Backend/meetnow` if Maven is installed.

3. In VS Code, open the repository root and start **Launch Meetnow backend** from Run and Debug.

   Locally the backend listens on port `8080` with context path `/api/v1.0`; on Render it uses the platform-provided `PORT`. `MONGODB_URI` and `JWT_SECRET_KEY` are required.

4. Configure Render environment variables: `MONGODB_URI`, a strong unique `JWT_SECRET_KEY`, `AGORA_APP_ID`, `AGORA_APP_CERTIFICATE`, and `APP_CORS_ALLOWED_ORIGINS` set to the deployed Vercel origin (for example, `https://your-app.vercel.app`, with no path or trailing slash). Keep `AGORA_APP_CERTIFICATE` backend-only.

---

### 🎨 Frontend Setup

1. Navigate to the frontend folder:
    ```bash
    cd Frontend/client
    ```

2. Install dependencies:
    ```bash
    npm install
    ```

3. Create a `.env` file in `Frontend/client/`:

    ```env
    VITE_BACKEND_URL=http://localhost:8080/api/v1.0
    VITE_AGORA_APP_ID=your_agora_app_id
    ```

4. Start the React development server:
    ```bash
    npm run dev
    ```

   On Vercel, set `VITE_BACKEND_URL` to the Render backend URL including `/api/v1.0` and `VITE_AGORA_APP_ID` to your public Agora App ID. Vite environment variables are embedded in the frontend bundle; never put the Agora App Certificate in a `VITE_` variable.

---

## 🔐 Authentication Flow

- JWT-based authentication
- Secure API routes
- Role-based access control (optional)

---

## 🔌 WebSocket Configuration

- **Endpoint:** `ws://localhost:8080/api/v1.0/ws/chat/{roomId}` (use `wss://` with the deployed HTTPS backend)
- Uses STOMP over SockJS
- Subscriptions:
  - `/topic/chat/{roomId}`
  - `/topic/participants/{roomId}`

---

## 🗂️ Sample REST API Endpoints

| Method | Endpoint                 | Description              |
|--------|--------------------------|--------------------------|
| POST   | `/api/auth/register`     | Register a new user      |
| POST   | `/api/auth/login`        | User login               |
| GET    | `/api/meetings`          | Get all meetings         |
| POST   | `/api/meetings/create`   | Create a meeting room    |
| GET    | `/api/meetings/{id}`     | Join a meeting by ID     |

---

## 🧠 Agora Integration

1. Create an account at [Agora.io](https://www.agora.io/)
2. Create a project and get the **App ID**
3. Add the **App ID** to:
   - `application.properties` (backend)
   - `.env` (frontend)
4. Agora handles video/audio streaming

---

## 📷 UI Preview

> *(Replace these with your real images)*

![Dashboard](https://your-image-url.com/dashboard.png)
![Meeting Room](https://your-image-url.com/meeting-room.png)

---

## 📌 Future Improvements

- 🖥️ Screen Sharing
- 📼 Cloud Recording
- 🛎️ Notification System
- 🔎 Meeting Search/Filter
- 🧾 Meeting Summary & Notes
- 📱 PWA Support

---

## 📦 Tools Used

- Postman
- MySQL Workbench
- Agora Developer Console
- IntelliJ / VSCode

---

## 🤝 Contribution

Contributions are welcome! To get started:

```bash
# Fork and clone this repo
git clone https://github.com/yourusername/videoconnect.git
cd videoconnect
