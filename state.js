import { Ball } from './ball.js';

class StateManager {
    constructor(storage) {
        this.storage = storage;
        this.balls = [];
        this.history = [];
        this.redoStack = [];
        this.MAX_HISTORY = 50;
    }

    async init() {
        const [balls, history, redoStack] = await Promise.all([
            this.storage.load('balls'),
            this.storage.load('history'),
            this.storage.load('redoStack')
        ]);

        // Reconstruct Ball objects from plain objects
        this.balls = (balls || []).map(b => {
            const ball = new Ball(b.name, b.responsible, b.ticket, b.importance, b.dueDate);
            ball.id = b.id;
            ball.createdAt = b.createdAt;
            ball.lastChecked = b.lastChecked;
            ball.resolved = b.resolved;
            return ball;
        });
        this.history = history || [];
        this.redoStack = redoStack || [];
        return this;
    }

    async addBall(ball) {
        this.balls.push(ball);
        await this.save();
    }

    async updateBall(ballId, updates) {
        const ball = this.balls.find(b => b.id === ballId);
        if (ball) {
            Object.assign(ball, updates);
            await this.save();
        }
    }

    async deleteBall(ballId) {
        const index = this.balls.findIndex(b => b.id === ballId);
        if (index !== -1) {
            this.balls.splice(index, 1);
            await this.save();
        }
    }

    async addToHistory(action) {
        this.history.push(action);
        this.redoStack = []; // Clear redo stack when new action is added
        if (this.history.length > this.MAX_HISTORY) {
            this.history.shift();
        }
        await this.save();
    }

    async undo() {
        const lastAction = this.history.pop();
        if (lastAction) {
            if (lastAction.type === 'delete') {
                const restoredBall = new Ball(
                    lastAction.previousState.name,
                    lastAction.previousState.responsible,
                    lastAction.previousState.ticket,
                    lastAction.previousState.importance,
                    lastAction.previousState.dueDate
                );
                restoredBall.id = lastAction.previousState.id;
                restoredBall.lastChecked = lastAction.previousState.lastChecked;
                restoredBall.createdAt = lastAction.previousState.createdAt;
                restoredBall.resolved = lastAction.previousState.resolved;

                this.balls.splice(lastAction.previousIndex, 0, restoredBall);
                this.redoStack.push({
                    ballId: lastAction.ballId,
                    type: 'delete',
                    previousState: { ...restoredBall },
                    previousIndex: lastAction.previousIndex,
                    timestamp: lastAction.timestamp
                });
            } else {
                const ball = this.balls.find(b => b.id === lastAction.ballId);
                if (ball) {
                    const redoAction = {
                        ballId: ball.id,
                        type: lastAction.type,
                        previousState: { ...ball },
                        timestamp: lastAction.timestamp
                    };
                    this.redoStack.push(redoAction);
                    
                    if (lastAction.type === 'check') {
                        ball.lastChecked = lastAction.previousChecked;
                    } else {
                        // Create a new Ball instance with the previous state
                        const previousBall = new Ball(
                            lastAction.previousState.name,
                            lastAction.previousState.responsible,
                            lastAction.previousState.ticket,
                            lastAction.previousState.importance,
                            lastAction.previousState.dueDate
                        );
                        previousBall.id = lastAction.previousState.id;
                        previousBall.lastChecked = lastAction.previousState.lastChecked;
                        previousBall.createdAt = lastAction.previousState.createdAt;
                        previousBall.resolved = lastAction.previousState.resolved;
                        
                        // Replace the current ball with the reconstructed one
                        const index = this.balls.indexOf(ball);
                        this.balls[index] = previousBall;
                    }
                }
            }
        }
        await this.save();
        return lastAction;
    }

    async redo() {
        const nextAction = this.redoStack.pop();
        if (nextAction) {
            if (nextAction.type === 'delete') {
                const ball = this.balls.find(b => b.id === nextAction.ballId);
                if (ball) {
                    const undoAction = {
                        ballId: ball.id,
                        type: 'delete',
                        previousState: { ...ball },
                        previousIndex: nextAction.previousIndex,
                        timestamp: nextAction.timestamp
                    };
                    this.history.push(undoAction);
                    this.balls.splice(this.balls.indexOf(ball), 1);
                }
            } else {
                const ball = this.balls.find(b => b.id === nextAction.ballId);
                if (ball) {
                    const undoAction = {
                        ballId: ball.id,
                        type: nextAction.type,
                        previousState: { ...ball },
                        timestamp: nextAction.timestamp
                    };
                    if (nextAction.type === 'check') {
                        undoAction.previousChecked = ball.lastChecked;
                        ball.lastChecked = nextAction.previousState.lastChecked;
                    } else {
                        // Create a new Ball instance with the next state
                        const nextBall = new Ball(
                            nextAction.previousState.name,
                            nextAction.previousState.responsible,
                            nextAction.previousState.ticket,
                            nextAction.previousState.importance,
                            nextAction.previousState.dueDate
                        );
                        nextBall.id = nextAction.previousState.id;
                        nextBall.lastChecked = nextAction.previousState.lastChecked;
                        nextBall.createdAt = nextAction.previousState.createdAt;
                        nextBall.resolved = nextAction.previousState.resolved;
                        
                        // Replace the current ball with the reconstructed one
                        const index = this.balls.indexOf(ball);
                        this.balls[index] = nextBall;
                    }
                    this.history.push(undoAction);
                }
            }
        }
        await this.save();
        return nextAction;
    }

    async save() {
        await Promise.all([
            this.storage.save('balls', this.balls),
            this.storage.save('history', this.history),
            this.storage.save('redoStack', this.redoStack)
        ]);
    }

    getBalls() {
        return this.balls;
    }

    getHistoryLength() {
        return this.history.length;
    }

    getRedoStackLength() {
        return this.redoStack.length;
    }

    getBallById(id) {
        return this.balls.find(b => b.id === id);
    }
}

class LocalStorageAdapter {
    async load(key) {
        const data = localStorage.getItem(key);
        return data ? JSON.parse(data) : null;
    }

    async save(key, value) {
        localStorage.setItem(key, JSON.stringify(value));
    }
}

// Example of what a remote storage adapter might look like
class RemoteStorageAdapter {
    constructor(apiUrl) {
        this.apiUrl = apiUrl;
    }

    async load(key) {
        const response = await fetch(`${this.apiUrl}/${key}`);
        return response.json();
    }

    async save(key, value) {
        await fetch(`${this.apiUrl}/${key}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(value)
        });
    }
}

// Create and export the state manager instance
const state = new StateManager(new LocalStorageAdapter());
export { state, StateManager, LocalStorageAdapter, RemoteStorageAdapter }; 