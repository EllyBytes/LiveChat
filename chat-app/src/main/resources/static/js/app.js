let stompClient = null;
let currentUsername = null;
let allUsers = new Set();
let onlineUsers = new Set();
let reconnectAttempts = 0;
const maxReconnectAttempts = 5;
const reconnectDelay = 5000;

function isTokenExpired(token) {
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        const expiry = payload.exp * 1000;
        return Date.now() > expiry;
    } catch (e) {
        return true;
    }
}

function extractUsernameFromToken(token) {
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return payload.sub;
    } catch (e) {
        console.error('Error extracting username from token:', e);
        return null;
    }
}

function loadChatHistory(roomId) {
    const token = localStorage.getItem('jwt');
    fetch(`/history/${roomId}`, {
        method: 'GET',
        headers: { 'Authorization': 'Bearer ' + token }
    }).then(response => {
        if (!response.ok) throw new Error('Failed to load chat history: ' + response.statusText);
        return response.json();
    }).then(messages => {
        console.log('Loaded chat history:', messages);
        messages.forEach(message => {
            allUsers.add(message.sender);
            showMessage(message, false);
        });
        console.log('All users after loading history:', Array.from(allUsers));
        updateUserList(); // Ensure user list is updated after loading history
    }).catch(error => {
        console.error('Error loading chat history:', error);
        alert('Failed to load chat history: ' + error.message);
    });
}

function connect() {
    const token = localStorage.getItem('jwt');
    if (!token || isTokenExpired(token)) {
        localStorage.removeItem('jwt');
        window.location.href = '/login.html';
        return;
    }

    const username = extractUsernameFromToken(token);
    if (!username) {
        alert('Failed to extract username from token');
        localStorage.removeItem('jwt');
        window.location.href = '/login.html';
        return;
    }

    const urlParams = new URLSearchParams(window.location.search);
    const roomId = urlParams.get('roomId');
    if (!roomId) {
        alert('Room ID is missing');
        window.location.href = '/dashboard.html';
        return;
    }

    currentUsername = username;
    allUsers = new Set();
    onlineUsers = new Set();

    // Disconnect existing client if it exists
    if (stompClient) {
        stompClient.disconnect(() => {
            console.log('Disconnected previous WebSocket client');
        });
    }

    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({ 'Authorization': 'Bearer ' + token }, () => {
        console.log('Connected to WebSocket');
        reconnectAttempts = 0; // Reset reconnect attempts on successful connection

        document.getElementById('messages').innerHTML = '';
        document.getElementById('userList').innerHTML = '';

        loadChatHistory(roomId);

        // Subscribe to messages
       stompClient.subscribe('/topic/messages/' + roomId, (message) => {
           console.log('Received message:', message.body);
           const msg = JSON.parse(message.body);
           allUsers.add(msg.sender);
           showMessage(msg, true);
           console.log('All users after new message:', Array.from(allUsers));
           updateUserList();
       }, { id: 'message-sub-' + roomId }, (error) => {
           console.error('Error in message subscription:', error);
       });

        // Subscribe to typing events
        stompClient.subscribe('/topic/typing/' + roomId, (event) => {
            console.log('Received typing event:', event.body);
            showTyping(JSON.parse(event.body));
        }, { id: 'typing-sub-' + roomId });

        // Subscribe to user list updates
        stompClient.subscribe('/topic/users/' + roomId, (event) => {
            console.log('Received user list update:', event.body);
            const users = JSON.parse(event.body);
            onlineUsers = new Set(users);
            console.log('Online users updated:', Array.from(onlineUsers));
            updateUserList();
        }, { id: 'users-sub-' + roomId });

        // Subscribe to deletion events (using reaction channel)
        stompClient.subscribe('/topic/reactions/' + roomId, (event) => {
            console.log('Received reaction/deletion event:', event.body);
            const data = JSON.parse(event.body);
            if (data.messageId) {
                const messageElement = document.getElementById(`msg-${data.messageId}`);
                if (messageElement) {
                    messageElement.remove();
                }
            }
        }, { id: 'reactions-sub-' + roomId });

        // Send join event
        stompClient.send('/app/join/' + roomId, {}, JSON.stringify({ sender: username }));

        // Handle disconnect
        window.addEventListener('beforeunload', () => {
            if (stompClient) {
                stompClient.send('/app/leave/' + roomId, {}, JSON.stringify({ sender: username }));
                stompClient.disconnect(() => {
                    console.log('Disconnected from WebSocket');
                });
            }
        }, { once: true });
    }, (error) => {
        console.error('WebSocket connection error:', error);
        if (reconnectAttempts < maxReconnectAttempts) {
            console.log(`Reconnecting to WebSocket in ${reconnectDelay/1000} seconds... (Attempt ${reconnectAttempts + 1}/${maxReconnectAttempts})`);
            setTimeout(() => {
                reconnectAttempts++;
                connect();
            }, reconnectDelay);
        } else {
            alert('Failed to connect to chat server after multiple attempts. Please refresh the page.');
            window.location.href = '/dashboard.html';
        }
    });
}

function sendMessage() {
    const messageInput = document.getElementById('messageInput');
    const urlParams = new URLSearchParams(window.location.search);
    const roomId = urlParams.get('roomId');
    if (messageInput.value && stompClient && stompClient.connected) {
        const chatMessage = {
            sender: currentUsername,
            content: messageInput.value,
            roomId: roomId,
            id: `temp-${Date.now()}`, // Temporary ID until server assigns a real one
            timestamp: new Date().toISOString().replace('T', ' ').substring(0, 19), // Temporary timestamp
            mediaType: "TEXT"
        };
        console.log('Sending message:', chatMessage);

        // Display the message immediately (optimistic update)
       // showMessage(chatMessage, true);

        // Send the message to the server
        stompClient.send('/app/sendMessage/' + roomId, {}, JSON.stringify({
            sender: currentUsername,
            content: messageInput.value,
            roomId: roomId
        }));

        messageInput.value = '';
    } else {
        alert('Not connected to the chat server. Please try refreshing the page.');
    }
}

function uploadFile() {
    const fileInput = document.getElementById('fileInput');
    const urlParams = new URLSearchParams(window.location.search);
    const roomId = urlParams.get('roomId');
    if (fileInput.files.length > 0 && stompClient && stompClient.connected) {
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        formData.append('username', currentUsername);
        formData.append('roomId', roomId);
        fetch('/upload', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + localStorage.getItem('jwt') },
            body: formData
        }).then(response => {
            if (!response.ok) throw new Error('Upload failed: ' + response.statusText);
            return response.text();
        }).then(mediaUrl => {
            console.log('Uploaded:', mediaUrl);
            fileInput.value = '';
        }).catch(error => {
            console.error('Upload failed:', error);
            alert('Failed to upload file: ' + error.message);
        });
    } else {
        alert('Not connected to the chat server. Please try refreshing the page.');
    }
}

function deleteMessage(messageId) {
    const urlParams = new URLSearchParams(window.location.search);
    const roomId = urlParams.get('roomId');
    fetch(`/message/${roomId}/${messageId}`, {
        method: 'DELETE',
        headers: { 'Authorization': 'Bearer ' + localStorage.getItem('jwt') }
    }).then(response => {
        if (!response.ok) throw new Error('Delete failed: ' + response.statusText);
        console.log('Message deleted:', messageId);
    }).catch(error => {
        console.error('Delete failed:', error);
        alert('Failed to delete message: ' + error.message);
    });
}

function showMessage(message, isNewMessage = false) {
    console.log('Processing message for display:', message);
    const existingMessage = document.getElementById(`msg-${message.id}`);
    if (existingMessage) {
        if (existingMessage.id.startsWith('msg-temp-') && !message.id.startsWith('temp-')) {
            console.log(`Replacing temp message ${existingMessage.id} with server message ${message.id}`);
            existingMessage.remove();
        } else {
            console.log(`Message with ID ${message.id} already exists, skipping display.`);
            return;
        }
    }

    const messages = document.getElementById('messages');
    if (!messages) {
        console.error('Messages container not found in DOM');
        return;
    }

    const div = document.createElement('div');
    div.className = `message ${message.sender === currentUsername ? 'own' : 'other'}`;
    div.id = `msg-${message.id}`;
    div.dataset.timestamp = new Date(message.timestamp).getTime();

    const contentWrapper = document.createElement('div');
    contentWrapper.className = 'relative';

    const contentDiv = document.createElement('div');
    if (message.mediaType === 'TEXT') {
        contentDiv.textContent = `${message.sender}: ${message.content}`;
    } else {
        const media = message.mediaType === 'IMAGE'
            ? `<img src="${message.mediaUrl}" class="max-w-full rounded" />`
            : `<video src="${message.mediaUrl}" class="max-w-full rounded" controls></video>`;
        contentDiv.innerHTML = `${message.sender}: ${media}`;
    }
    contentWrapper.appendChild(contentDiv);

    const timestampDiv = document.createElement('div');
    timestampDiv.className = 'timestamp absolute bottom-0 right-0 text-xs text-gray-500';
    timestampDiv.textContent = message.timestamp;
    contentWrapper.appendChild(timestampDiv);

    if (message.sender === currentUsername) {

    }

    div.appendChild(contentWrapper);

    if (isNewMessage) {
        messages.appendChild(div);
    } else {
        const existingMessages = Array.from(messages.children);
        let inserted = false;
        for (let i = 0; i < existingMessages.length; i++) {
            const existingTimestamp = parseInt(existingMessages[i].dataset.timestamp);
            const newTimestamp = parseInt(div.dataset.timestamp);
            if (newTimestamp < existingTimestamp) {
                messages.insertBefore(div, existingMessages[i]);
                inserted = true;
                break;
            }
        }
        if (!inserted) {
            messages.appendChild(div);
        }
    }
    messages.scrollTop = messages.scrollHeight;
}

function showTyping(event) {
    const typingIndicator = document.getElementById('typingIndicator');
    if (event.sender !== currentUsername) {
        typingIndicator.textContent = `${event.sender} is typing...`;
        setTimeout(() => {
            typingIndicator.textContent = '';
        }, 2000);
    }
}

function updateUserList() {
    const userList = document.getElementById('userList');
    if (!userList) {
        console.error('userList element not found in DOM');
        return;
    }
    userList.innerHTML = '';

    console.log('Updating user list. All users:', Array.from(allUsers), 'Online users:', Array.from(onlineUsers));

    if (allUsers.size === 0) {
        console.warn('No users to display in the chat room');
        const noUsersDiv = document.createElement('div');
        noUsersDiv.className = 'text-gray-500 italic';
        noUsersDiv.textContent = 'No users in this chat room yet.';
        userList.appendChild(noUsersDiv);
        return;
    }

    allUsers.forEach(username => {
        const userCard = document.createElement('div');
        userCard.className = 'user-card relative';

        const avatar = document.createElement('div');
        avatar.className = 'user-avatar';
        avatar.textContent = username.charAt(0).toUpperCase();
        userCard.appendChild(avatar);

        const nameSpan = document.createElement('span');
        nameSpan.textContent = username;
        userCard.appendChild(nameSpan);

        const statusDot = document.createElement('span');
        statusDot.className = `absolute top-0 right-0 w-4 h-4 rounded-full border-2 border-white ${
            onlineUsers.has(username) ? 'bg-green-500' : 'bg-gray-500'
        }`;
        userCard.appendChild(statusDot);

        userList.appendChild(userCard);
    });
}

// Event Listeners
document.addEventListener('DOMContentLoaded', () => {
    connect();

    const sendBtn = document.getElementById('sendBtn');
    const fileInput = document.getElementById('fileInput');
    const messageInput = document.getElementById('messageInput');

    if (sendBtn) sendBtn.addEventListener('click', sendMessage);
    if (fileInput) fileInput.addEventListener('change', uploadFile);
    if (messageInput) {
        messageInput.addEventListener('input', () => {
            const urlParams = new URLSearchParams(window.location.search);
            const roomId = urlParams.get('roomId');
            if (stompClient && stompClient.connected) {
                stompClient.send('/app/typing/' + roomId, {}, JSON.stringify({
                    sender: currentUsername
                }));
            }
        });
    }
});