
# LiveChat

A real-time chat application built with Spring Boot, WebSocket, Redis, MySQL, and a JavaScript frontend. This application allows users to join chat rooms, send text messages, upload media files (images/videos), and receive real-time updates. Messages are stored in a hybrid system using Redis for caching and MySQL for persistence, with WhatsApp-style message ordering (oldest at the top, newest at the bottom).

## Features
- **Real-Time Messaging**: Send and receive text messages and media files in real-time using WebSocket (STOMP over SockJS).
- **User Authentication**: JWT-based authentication for secure access.
- **Chat Rooms**: Users can join specific rooms to chat (e.g., `index.html?roomId=2`).
- **Hybrid Storage**:
    - Messages are cached in Redis for 24 hours for fast retrieval.
    - Messages are persistently stored in MySQL for long-term storage.
- **Message Ordering**: Messages are displayed in chronological order, WhatsApp-style (oldest at the top, newest at the bottom).
- **File Uploads**: Supports uploading images and videos, displayed inline in the chat.
- **Typing Indicators**: Shows when another user is typing.
- **Online Status**: Displays online/offline status of users in the room.
- **Message Deletion**: Users can delete their own messages, with real-time updates for all users.
- **Optimistic Updates**: Messages are displayed immediately for the sender (with a temporary ID) and updated with the server-assigned ID upon confirmation.
- **Error Handling**: Robust error handling for WebSocket connections, message serialization, and database operations.

## Tech Stack
- **Backend**:
    - Spring Boot (Java)
    - WebSocket (STOMP over SockJS)
    - Redis (for caching messages and session management)
    - MySQL (for persistent storage)
    - Gson (for JSON serialization/deserialization)
- **Frontend**:
    - JavaScript (with SockJS and STOMP client)
    - HTML/CSS
- **Dependencies**:
    - Maven (for dependency management)
    - Lombok (for reducing boilerplate code)
    - Spring Security (for JWT authentication)

## Prerequisites
- Java 17 or higher
- Maven
- MySQL Server
- Redis Server
- Node.js (optional, for frontend development)
- A modern web browser (e.g., Chrome, Firefox)

## Setup Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/ChatApp.git
cd ChatApp
```

### 2. Configure MySQL
1. Install MySQL if not already installed.
2. Create a database named `chat_app_db`:
   ```sql
   CREATE DATABASE chat_app_db;
   ```
3. Update the MySQL configuration in `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/chat_app_db?useSSL=false&serverTimezone=Asia/Kolkata
   spring.datasource.username=your_mysql_username
   spring.datasource.password=your_mysql_password
   spring.jpa.hibernate.ddl-auto=update
   spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
   spring.jpa.properties.hibernate.jdbc.time_zone=Asia/Kolkata
   ```

### 3. Configure Redis
1. Install Redis if not already installed.
    - On Windows, download and extract the Redis binaries (e.g., from [GitHub](https://github.com/microsoftarchive/redis/releases)).
    - On Linux/macOS, install via package manager (e.g., `sudo apt install redis-server`).
2. Start the Redis server:
   ```cmd
   C:\Redis>redis-server
   ```
3. Ensure Redis is running on the default port (6379). Update `application.properties` if necessary:
   ```properties
   spring.data.redis.host=localhost
   spring.data.redis.port=6379
   ```

### 4. Build and Run the Application
1. Build the project using Maven:
   ```cmd
   mvn clean install
   ```
2. Run the Spring Boot application:
   ```cmd
   mvn spring-boot:run
   ```
   The application will start on `http://localhost:8080`.

### 5. Access the Application
1. Open a web browser and navigate to:
   ```
   http://localhost:8080/login.html
   ```
2. Log in with a username (e.g., `Eileen` or `user2`). The application uses JWT for authentication, and a token will be stored in `localStorage`.
3. After logging in, you’ll be redirected to the dashboard (`dashboard.html`).
4. Join a chat room by navigating to a room URL, e.g.:
   ```
   http://localhost:8080/index.html?roomId=2
   ```

## Usage
1. **Login**:
    - Navigate to `/login.html`, enter a username, and log in.
2. **Join a Chat Room**:
    - From the dashboard, select a room or manually navigate to `/index.html?roomId=<roomId>` (e.g., `roomId=2`).
3. **Send Messages**:
    - Type a message in the input field and click "Send" to send a text message.
    - Use the file input to upload images or videos.
4. **Real-Time Updates**:
    - Messages from other users in the same room will appear in real-time.
    - Typing indicators will show when another user is typing.
5. **Delete Messages**:
    - Hover over your own messages to see a "Delete" button. Clicking it will remove the message for all users.
6. **View Online Users**:
    - The user list on the right shows all users who have participated in the room, with a green dot indicating online status.

## Project Structure
```
ChatApp/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/chat_app/
│   │   │       ├── config/               # Configuration classes (e.g., WebSocketConfig)
│   │   │       ├── controller/           # Controllers (e.g., ChatController)
│   │   │       ├── model/                # Entities (e.g., ChatMessage)
│   │   │       ├── repository/           # Repositories (e.g., MessageRepository)
│   │   │       ├── service/              # Services (e.g., MessageService)
│   │   │       └── ChatAppApplication.java
│   │   └── resources/
│   │       ├── static/                   # Frontend files (HTML, JS, CSS)
│   │       │   ├── app.js
│   │       │   ├── login.html
│   │       │   ├── dashboard.html
│   │       │   └── index.html
│   │       └── application.properties    # Configuration file
├── pom.xml                               # Maven dependencies
└── README.md                             # Project documentation
```

## Key Files
- **app.js**: Frontend logic for WebSocket communication, message display, and UI updates.
- **ChatController.java**: Handles WebSocket messages and HTTP requests (e.g., `/sendMessage`, `/history`).
- **MessageService.java**: Manages message storage in Redis and MySQL.
- **ChatMessage.java**: Entity class representing a chat message.
- **RedisMessagePublisher.java**: Publishes messages to WebSocket topics.

## Example Chat Flow
1. **User `Eileen` Logs In**:
    - Navigates to `/login.html`, logs in, and is redirected to `/dashboard.html`.
2. **Joins Room `2`**:
    - Navigates to `/index.html?roomId=2`.
    - Chat history is loaded from Redis (or MySQL if Redis is empty).
3. **Sends a Message**:
    - Types “Hello, user2!” and clicks "Send".
    - The message appears immediately in `Eileen`’s UI with a temporary ID.
    - The server assigns a UUID, saves the message to Redis and MySQL, and broadcasts it via WebSocket.
    - `Eileen`’s UI updates with the server-assigned ID, and `user2` sees the message in real-time.
4. **Uploads a File**:
    - Uploads an image, which is saved on the server and broadcasted via WebSocket.
    - Both users see the image in the chat.
5. **Deletes a Message**:
    - `Eileen` deletes her message, and it disappears for both users.

## Screenshots
*(Add screenshots here if desired, e.g., of the login page, chat room, and message display.)*

## Known Issues
- **Timezone Consistency**: Ensure your system clock is set to the correct timezone (e.g., `Asia/Kolkata`) to avoid timestamp discrepancies. The application is configured to use IST (`Asia/Kolkata`).
- **Redis Expiry**: Messages in Redis expire after 24 hours. If Redis is empty, the application falls back to MySQL, which may cause a slight delay.




## Contact
For questions or feedback, please contact [a208.eileenmas@example.com].
