import { state } from './state.js';
import { Ball } from './ball.js';

let editingBallId = null;

function updateHistoryButtons() {
    document.getElementById('undoButton').disabled = state.getHistoryLength() === 0;
    document.getElementById('redoButton').disabled = state.getRedoStackLength() === 0;
}

async function addBall() {
    const name = document.getElementById('name').value;
    const responsible = document.getElementById('responsible').value;
    const ticket = document.getElementById('ticket').value;
    const importance = document.getElementById('importance').value;
    const dueDate = document.getElementById('dueDate').value;

    if (!name || !responsible) {
        alert('Name and responsible person are required!');
        return;
    }

    const ball = new Ball(name, responsible, ticket, importance, dueDate);
    await state.addBall(ball);
    updateDisplay();
    clearForm();
}

async function checkBall(ballId, event) {
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }
    const ball = state.getBallById(ballId);
    if (!ball) return;

    const previousChecked = ball.lastChecked;
    ball.lastChecked = Date.now();
    await state.addToHistory({
        ballId: ball.id,
        type: 'check',
        previousChecked,
        timestamp: Date.now()
    });
    requestAnimationFrame(() => {
        updateDisplay();
    });
}

function startEdit(ballId, event) {
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }
    const ball = state.getBallById(ballId);
    if (!ball) return;

    editingBallId = ball.id;
    
    // Populate form
    document.getElementById('name').value = ball.name;
    document.getElementById('responsible').value = ball.responsible;
    document.getElementById('ticket').value = ball.ticket || '';
    document.getElementById('importance').value = ball.importance;
    if (ball.dueDate) {
        const date = new Date(ball.dueDate);
        document.getElementById('dueDate').value = date.toISOString().slice(0, 16);
    } else {
        document.getElementById('dueDate').value = '';
    }

    // Update buttons
    const addButton = document.querySelector('.add-form button');
    addButton.style.display = 'none';
    
    // Add update and cancel buttons if they don't exist
    if (!document.querySelector('.form-buttons')) {
        const formButtons = document.createElement('div');
        formButtons.className = 'form-buttons';
        formButtons.innerHTML = `
            <button onclick="updateBall()" class="update-button">Update Item</button>
            <button onclick="cancelEdit()" class="cancel-button">Cancel</button>
        `;
        addButton.parentNode.appendChild(formButtons);
    }

    // Scroll form into view
    document.querySelector('.add-form').scrollIntoView({ behavior: 'smooth' });
}

function cancelEdit() {
    editingBallId = null;
    clearForm();
    resetFormButtons();
}

function resetFormButtons() {
    const addButton = document.querySelector('.add-form button');
    addButton.style.display = '';
    const formButtons = document.querySelector('.form-buttons');
    if (formButtons) {
        formButtons.remove();
    }
}

async function updateBall() {
    const name = document.getElementById('name').value;
    const responsible = document.getElementById('responsible').value;
    const ticket = document.getElementById('ticket').value;
    const importance = document.getElementById('importance').value;
    const dueDate = document.getElementById('dueDate').value;

    if (!name || !responsible) {
        alert('Name and responsible person are required!');
        return;
    }

    const balls = state.getBalls();
    const ball = balls.find(b => b.id === editingBallId);
    if (ball) {
        const previousState = { ...ball };
        const updates = {
            name,
            responsible,
            ticket,
            importance,
            dueDate: dueDate ? new Date(dueDate).getTime() : null
        };
        
        await state.updateBall(ball.id, updates);
        await state.addToHistory({
            ballId: ball.id,
            type: 'edit',
            previousState,
            timestamp: Date.now()
        });
    }

    editingBallId = null;
    clearForm();
    resetFormButtons();
    updateDisplay();
}

async function resolveBall(ballId, event) {
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }
    const ball = state.getBallById(ballId);
    if (!ball) return;

    const previousState = { ...ball };
    ball.resolved = !ball.resolved;
    
    await state.updateBall(ball.id, { resolved: ball.resolved });
    await state.addToHistory({
        ballId: ball.id,
        type: 'resolve',
        previousState,
        timestamp: Date.now()
    });
    
    requestAnimationFrame(() => {
        updateDisplay();
    });
}

async function deleteBall(ballId, event) {
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }
    
    const ball = state.getBallById(ballId);
    if (!ball) return;

    const previousState = { ...ball };
    const previousIndex = state.getBalls().indexOf(ball);
    
    await state.deleteBall(ballId);
    await state.addToHistory({
        ballId: ball.id,
        type: 'delete',
        previousState,
        previousIndex,
        timestamp: Date.now()
    });
    
    requestAnimationFrame(() => {
        updateDisplay();
    });
}

async function undo() {
    await state.undo();
    updateHistoryButtons();
    requestAnimationFrame(() => {
        updateDisplay();
    });
}

async function redo() {
    await state.redo();
    updateHistoryButtons();
    requestAnimationFrame(() => {
        updateDisplay();
    });
}

function clearForm() {
    document.getElementById('name').value = '';
    document.getElementById('responsible').value = '';
    document.getElementById('ticket').value = '';
    document.getElementById('importance').value = 'low';
    document.getElementById('dueDate').value = '';
}

function updateDisplay() {
    const container = document.getElementById('ballContainer');
    container.innerHTML = '';

    // Sort balls by urgency score
    const balls = state.getBalls();
    const sortedBalls = [...balls].sort((a, b) => b.getUrgencyScore() - a.getUrgencyScore());

    sortedBalls.forEach((ball) => {
        const element = document.createElement('div');
        const statusClass = ball.getStatusClass();
        
        element.className = `ball importance-${ball.importance} ${statusClass} ${ball.resolved ? 'resolved' : ''}`;
        
        const daysSinceChecked = Math.floor((Date.now() - ball.lastChecked) / (1000 * 60 * 60 * 24));
        const timeSinceChecked = daysSinceChecked > 0 
            ? `${daysSinceChecked} days ago`
            : `${Math.floor((Date.now() - ball.lastChecked) / (1000 * 60))} minutes ago`;
        
        element.innerHTML = `
            <div class="ball-info">
                <div class="ball-name">${ball.name}</div>
                <div class="ball-meta">
                    <div class="ball-meta-line">
                        <span>${ball.responsible}</span>
                        ${ball.ticket ? `<span><a href="https://github.com/issues/${ball.ticket.replace('GH-', '')}" target="_blank">${ball.ticket}</a></span>` : ''}
                        <span class="importance-badge">${ball.importance}</span>
                    </div>
                    ${ball.formatDueDate() ? `<div class="ball-meta-line">${ball.formatDueDate()}</div>` : ''}
                    <div class="ball-meta-line">
                        <span>Last checked ${timeSinceChecked}</span>
                        ${ball.resolved ? '<span>Resolved</span>' : ''}
                    </div>
                </div>
            </div>
            <div class="ball-actions">
                <div class="edit-icon" onclick="startEdit('${ball.id}', event)">✎</div>
                <div class="check-icon" onclick="checkBall('${ball.id}', event)">✓</div>
                <div class="resolve-icon" onclick="resolveBall('${ball.id}', event)">${ball.resolved ? '⚐' : '⚑'}</div>
                <div class="delete-icon" onclick="deleteBall('${ball.id}', event)">×</div>
            </div>
        `;
        
        container.appendChild(element);
    });
}

// Load saved balls on page load
window.onload = async function() {
    console.log("Loading balls");
    await state.init();
    
    console.log("Balls loaded");
    console.log(state.getBalls());

    if (state.getBalls().length === 0) {
        // Test data
        const testData = [
            { name: "Update Documentation", responsible: "Sarah", ticket: "GH-123", importance: "high", dueDate: "2024-01-20" },
            { name: "Fix Security Vulnerability", responsible: "John", ticket: "GH-456", importance: "critical", dueDate: new Date(Date.now() + 1000 * 60 * 60 * 2).toISOString() }, // 2 hours from now
            { name: "Code Review Backend PR", responsible: "Mike", ticket: "GH-789", importance: "medium", dueDate: new Date(Date.now() + 1000 * 60 * 60 * 24).toISOString() }, // 1 day from now
            { name: "Weekly Team Meeting", responsible: "Emma", ticket: "", importance: "medium" },
            { name: "Deploy to Production", responsible: "Alex", ticket: "GH-234", importance: "critical", dueDate: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString() }, // 1 day ago
            { name: "Update Dependencies", responsible: "Tom", ticket: "GH-567", importance: "low" },
            { name: "User Testing Session", responsible: "Lisa", ticket: "GH-890", importance: "medium", dueDate: new Date(Date.now() + 1000 * 60 * 60 * 24 * 3).toISOString() }, // 3 days from now
            { name: "Backup Database", responsible: "Dave", ticket: "GH-345", importance: "high" },
            { name: "Refactor Auth Module", responsible: "Rachel", ticket: "GH-678", importance: "medium" },
            { name: "Performance Testing", responsible: "Chris", ticket: "GH-901", importance: "low" },
            { name: "Update API Docs", responsible: "Nina", ticket: "GH-432", importance: "low", dueDate: new Date(Date.now() + 1000 * 60 * 60 * 24 * 14).toISOString() }, // 14 days from now
            { name: "Fix Mobile Layout", responsible: "Paul", ticket: "GH-765", importance: "medium" },
            { name: "Customer Support Meeting", responsible: "Helen", ticket: "", importance: "medium", dueDate: new Date(Date.now() + 1000 * 60 * 60 * 24 * 2).toISOString() }, // 2 days from now
            { name: "Load Testing", responsible: "Mark", ticket: "GH-098", importance: "low" },
            { name: "Security Audit", responsible: "Julia", ticket: "GH-321", importance: "high", dueDate: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString() } // 2 hours ago
        ];

        // Add test data with staggered check times
        for (const item of testData) {
            const ball = new Ball(item.name, item.responsible, item.ticket, item.importance, item.dueDate);
            const index = testData.indexOf(item);
            const minutesAgo = Math.floor(10 + Math.pow(index / 14, 2) * (10 * 24 * 60));
            ball.lastChecked = Date.now() - (minutesAgo * 60 * 1000);
            await state.addBall(ball);
        }
    }
    
    updateHistoryButtons();
    updateDisplay();
};
// Update display every minute to refresh timestamps
setInterval(updateDisplay, 60000);

// Make event handlers global
window.addBall = addBall;
window.checkBall = checkBall;
window.startEdit = startEdit;
window.updateBall = updateBall;
window.cancelEdit = cancelEdit;
window.resolveBall = resolveBall;
window.deleteBall = deleteBall;
window.undo = undo;
window.redo = redo;