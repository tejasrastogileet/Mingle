# 🎥 Mingle: Video Conferencing Platform

*Meet. Connect. Mingle.*

A full-stack video conferencing app built with **Spring Boot, MongoDB, WebSockets, AgoraRTC, React, Vite and Tailwind CSS**.

## Features
- 🔐 Register / login with JWT-secured APIs
- 📅 Create and join meeting rooms
- 🎥 Real-time video and audio via AgoraRTC
- 💬 Live in-meeting chat (STOMP over SockJS)
- 👥 Live participant updates
- 📝 Meeting history
- 📱 Responsive UI

## Tech stack
| Layer | Technologies |
|---|---|
| Backend | Java 17, Spring Boot, Spring Security + JWT, Spring Data MongoDB, WebSockets (STOMP/SockJS), REST |
| Frontend | React (Vite), Tailwind CSS, Axios, React Router, AgoraRTC SDK, SockJS + STOMP client |
| Database | MongoDB Atlas |
| Deployment | Render (backend), Vercel (frontend) |
| Tools | Maven, Postman, Agora Developer Console |

## Getting started
**Prerequisites:** Java 17+, Node.js 18+, MongoDB Atlas (or local MongoDB), Agora account

### Backend
```bash
cd Backend/meetnow
cp .env.example .env                                           # set MONGODB_URI
cp application-local.properties.example application-local.properties
./mvnw spring-boot:run                                         # http://localhost:8080/api/v1.0
```
Required env vars: `MONGODB_URI`, `JWT_SECRET_KEY` (unique in production), `AGORA_APP_ID`, `AGORA_APP_CERTIFICATE` (backend only), `APP_CORS_ALLOWED_ORIGINS` (your Vercel origin).

### Frontend
```bash
cd Frontend/client
npm install
```
Create `.env`:
```
VITE_BACKEND_URL=http://localhost:8080/api/v1.0
VITE_AGORA_APP_ID=your_agora_app_id
```
```bash
npm run dev
```
Never put the Agora App Certificate in a `VITE_` variable; Vite embeds those in the browser bundle.

## WebSocket
- Endpoint: `/api/v1.0/ws/chat/{roomId}` (use `wss://` when deployed)
- Subscriptions: `/topic/chat/{roomId}`, `/topic/participants/{roomId}`

## Main REST endpoints
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register |
| POST | `/api/auth/login` | Login |
| GET | `/api/meetings` | List meetings |
| POST | `/api/meetings/create` | Create a room |
| GET | `/api/meetings/{id}` | Join by ID |

## Roadmap
- [ ] Screen sharing
- [ ] Cloud recording
- [ ] Notifications
- [ ] Meeting search / filter
- [ ] Meeting summary and notes
- [ ] PWA support

## Author
Tejas Rastogi · [GitHub](https://github.com/tejasrastogileet)
