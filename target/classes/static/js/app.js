// Setup APIs
const API_URL = '/api';

// Config for global fetch wrapper to handle sessions
async function fetchSecure(url, options = {}) {
    options.credentials = 'include'; // Force session cookies
    const res = await fetch(url, options);
    
    // Auto-Logout if Session Expired or unauthorized
    if (res.status === 401 || res.status === 403) {
        if (window.location.pathname.indexOf('index.html') === -1 && window.location.pathname !== '/') {
            sessionStorage.removeItem('user');
            window.location.href = 'index.html';
        }
    }
    return res;
}

// Live clock
function updateClock() {
    const timeEl = document.getElementById('currentTime');
    if (timeEl) {
        const now = new Date();
        timeEl.innerText = now.toLocaleTimeString([], { hour12: true });
    }
}
setInterval(updateClock, 1000);

// Helper Status Badge
function formatStatusBadge(status) {
    if (!status) return `<span class="badge">Unknown</span>`;
    const s = status.toLowerCase();
    if (s.includes('late')) {
        return `<span class="badge badge-late">${status}</span>`;
    }
    return `<span class="badge badge-ontime">${status}</span>`;
}

/* -------------------
   LOGIN LOGIC
------------------- */
const loginForm = document.getElementById('loginForm');
if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const employeeId = document.getElementById('employeeId').value;
        const passwordEl = document.getElementById('password');
        const password = passwordEl ? passwordEl.value : '';
        const errorDiv = document.getElementById('loginError');

        try {
            const res = await fetchSecure(`${API_URL}/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ employeeId, password })
            });

            if (res.ok) {
                const user = await res.json();
                sessionStorage.setItem('user', JSON.stringify(user));
                // Redirect logic
                if (user.role === 'ADMIN') {
                    window.location.href = 'admin.html';
                } else {
                    window.location.href = 'dashboard.html';
                }
            } else {
                const errText = await res.text();
                errorDiv.innerText = errText || 'Login Failed. Invalid credentials.';
                errorDiv.style.display = 'block';
            }
        } catch (err) {
            errorDiv.innerText = 'Network Error. Please try again.';
            errorDiv.style.display = 'block';
        }
    });
}

async function logout() {
    try {
        await fetchSecure(`${API_URL}/logout`, { method: 'POST' });
    } catch(e) {}
    sessionStorage.removeItem('user');
    window.location.href = 'index.html';
}

/* -------------------
   DASHBOARD LOGIC
------------------- */
function formatTime(timeArray) {
    if (!timeArray || timeArray.length < 2) return '--:--';
    const hours = timeArray[0].toString().padStart(2, '0');
    const mins = timeArray[1].toString().padStart(2, '0');
    return `${hours}:${mins}`;
}

async function initDashboard() {
    const user = JSON.parse(sessionStorage.getItem('user'));
    if (!user) return;

    document.getElementById('headerUserName').innerText = user.name;
    document.getElementById('welcomeMessage').innerText = `Good Morning, ${user.name.split(' ')[0]}`;
    document.getElementById('displayEmpId').innerText = user.employeeId;

    await loadTodayStatus();
    await loadHistory();
}

async function loadTodayStatus() {
    try {
        const res = await fetchSecure(`${API_URL}/attendance/today`);
        if (!res.ok) return;
        const data = await res.json();
        
        const checkInBtn = document.getElementById('checkInBtn');
        const checkOutBtn = document.getElementById('checkOutBtn');
        const badge = document.getElementById('todayStatusBadge');
        
        const inLabel = document.getElementById('todayCheckIn');
        const outLabel = document.getElementById('todayCheckOut');

        // Logic depending on status
        if (!data.id) { // Not checked in at all
            badge.innerText = "Not Checked-In";
            badge.className = "status-indicator status-missing";
            checkInBtn.disabled = false;
            checkOutBtn.disabled = true;
            inLabel.innerText = "--:--";
            outLabel.innerText = "--:--";
        } else {
            // Checked in
            inLabel.innerText = formatTime(data.checkInTime);
            checkInBtn.disabled = true;
            
            if (!data.checkOutTime) {
                // Not checked out yet
                badge.innerText = `Checked-In (${data.status})`;
                badge.className = data.status === 'Late' ? "status-indicator status-late" : "status-indicator status-present";
                checkOutBtn.disabled = false;
                checkOutBtn.classList.remove('btn-outline');
                checkOutBtn.classList.add('btn-primary');
                outLabel.innerText = "--:--";
            } else {
                // Done for the day!
                badge.innerText = "Completed for Today";
                badge.className = "status-indicator status-present";
                checkOutBtn.disabled = true;
                outLabel.innerText = formatTime(data.checkOutTime);
                checkOutBtn.classList.remove('btn-primary');
                checkOutBtn.classList.add('btn-outline');
            }
        }
    } catch (err) {
        console.error("Failed to load status", err);
    }
}

async function markAttendance(type) {
    const messageDiv = document.getElementById('actionMessage');
    
    try {
        const res = await fetchSecure(`${API_URL}/attendance/${type}`, {
            method: type === 'check-in' ? 'POST' : 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({}) // Backend extracts session identity
        });
        
        if (res.ok) {
            messageDiv.innerText = `${type === 'check-in' ? 'Checked in' : 'Checked out'} successfully!`;
            messageDiv.className = 'action-message success-msg';
            await loadTodayStatus();
            await loadHistory();
            setTimeout(() => { messageDiv.innerText = ''; }, 3000);
        } else {
            const errText = await res.text();
            messageDiv.innerText = errText || 'Error performing action';
            messageDiv.className = 'action-message error-msg';
        }
    } catch (err) {
        messageDiv.innerText = 'Network error. Try again.';
        messageDiv.className = 'action-message error-msg';
    }
}

async function loadHistory() {
    const tbody = document.getElementById('historyTableBody');
    if (!tbody) return;

    try {
        const res = await fetchSecure(`${API_URL}/attendance/history`);
        if (!res.ok) return;
        const data = await res.json();
        
        tbody.innerHTML = '';
        if (data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align: center;">No history found</td></tr>';
            return;
        }
        
        data.forEach(row => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${row.date.join('-')}</td>
                <td>${formatTime(row.checkInTime)}</td>
                <td>${row.checkOutTime ? formatTime(row.checkOutTime) : '-'}</td>
                <td>${formatStatusBadge(row.status)}</td>
                <td><strong>${row.totalHours}</strong></td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        console.error("Failed to load history", err);
    }
}

/* -------------------
   ADMIN LOGIC
------------------- */
async function initAdminDashboard() {
    const user = JSON.parse(sessionStorage.getItem('user'));
    if (user && user.name) {
         document.getElementById('headerUserName').innerText = user.name;
    }
    await loadAdminTable();
}

async function loadAdminTable() {
    const tbody = document.getElementById('adminTableBody');
    if (!tbody) return;

    try {
        const res = await fetchSecure(`${API_URL}/admin/attendance`);
        if (res.status === 403) {
             tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: red;">Forbidden: Admin access required</td></tr>';
             return;
        }
        if (!res.ok) return;

        const data = await res.json();
        
        tbody.innerHTML = '';
        if (data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align: center;">No records found</td></tr>';
            return;
        }
        
        data.forEach(row => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${row.employeeId}</strong></td>
                <td>${row.employeeName}</td>
                <td>${row.date.join('-')}</td>
                <td>${formatTime(row.checkInTime)}</td>
                <td>${row.checkOutTime ? formatTime(row.checkOutTime) : '-'}</td>
                <td>${formatStatusBadge(row.status)}</td>
                <td><strong>${row.totalHours}</strong></td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: red;">Failed to fetch data</td></tr>';
        console.error("Failed to load admin data", err);
    }
}

async function exportCsv() {
    try {
        const res = await fetchSecure(`${API_URL}/admin/attendance/export`);
        if (res.status === 403) {
             alert('Forbidden: Admin access required');
             return;
        }
        if (res.ok) {
            const blob = await res.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'attendance_report.csv';
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
        } else {
            alert('Failed to export CSV report');
        }
    } catch (err) {
        alert('Network error during export');
    }
}
