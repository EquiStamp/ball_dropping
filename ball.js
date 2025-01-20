export class Ball {
    constructor(name, responsible, ticket, importance, dueDate = null) {
        this.id = crypto.randomUUID();
        this.name = name;
        this.responsible = responsible;
        this.ticket = ticket;
        this.importance = importance;
        this.dueDate = dueDate ? new Date(dueDate).getTime() : null;
        this.createdAt = Date.now();
        this.lastChecked = Date.now();
        this.resolved = false;
    }

    getImportanceMultiplier() {
        return {
            'critical': 10,
            'high': 5,
            'medium': 2,
            'low': 1
        }[this.importance];
    }

    getUrgencyScore() {
        if (this.resolved) return -1; // Always at the bottom
        const importanceMultiplier = this.getImportanceMultiplier();
        const now = Date.now();
        
        // Calculate a freshness factor (0 to 1) based on time since last check
        // Exponential decay: very low when recently checked, rises quickly after a day
        const daysSinceChecked = (now - this.lastChecked) / (1000 * 60 * 60 * 24);
        const freshnessFactor = 1 - Math.exp(-daysSinceChecked / 2); // reaches ~0.63 after 2 days
        
        let baseUrgency;
        if (this.dueDate) {
            const timeUntilDue = this.dueDate - now;
            const daysUntilDue = timeUntilDue / (1000 * 60 * 60 * 24);
            
            if (timeUntilDue < 0) {
                // Overdue items
                baseUrgency = 1000000;
            } else if (daysUntilDue < 7) {
                // Due within a week
                baseUrgency = 10000 / Math.max(0.1, daysUntilDue);
            } else if (daysUntilDue < 30) {
                // Due within a month
                baseUrgency = 1000 / daysUntilDue;
            } else {
                // Due in the future
                baseUrgency = 10 / daysUntilDue;
            }
        } else {
            // No due date - base urgency just on time since check
            baseUrgency = 1000;
        }

        // Combine all factors
        return baseUrgency * importanceMultiplier * freshnessFactor;
    }

    getStatusClass() {
        if (!this.dueDate) {
            const daysSinceChecked = (Date.now() - this.lastChecked) / (1000 * 60 * 60 * 24);
            return daysSinceChecked > 7 ? 'aging' : '';
        }

        const timeUntilDue = this.dueDate - Date.now();
        const daysUntilDue = timeUntilDue / (1000 * 60 * 60 * 24);

        if (timeUntilDue < 0) return 'overdue';
        if (daysUntilDue < 2) return 'due-soon';
        return '';
    }

    formatDueDate() {
        if (!this.dueDate) return '';
        
        const date = new Date(this.dueDate);
        const now = new Date();
        const timeUntilDue = this.dueDate - now.getTime();
        const daysUntilDue = timeUntilDue / (1000 * 60 * 60 * 24);

        if (timeUntilDue < 0) {
            const daysOverdue = Math.abs(Math.floor(daysUntilDue));
            return `<span class="due-date overdue">Overdue by ${daysOverdue} day${daysOverdue !== 1 ? 's' : ''}</span>`;
        }

        if (daysUntilDue < 1) {
            const hoursUntilDue = timeUntilDue / (1000 * 60 * 60);
            return `<span class="due-date due-soon">Due in ${Math.floor(hoursUntilDue)} hours</span>`;
        }

        if (daysUntilDue < 7) {
            return `<span class="due-date due-soon">Due in ${Math.floor(daysUntilDue)} days</span>`;
        }

        return `<span class="due-date">Due ${date.toLocaleDateString()}</span>`;
    }

    addCheck() {
        const timestamp = Date.now();
        this.lastChecked = timestamp;
    }

    revertToCheck(timestamp) {
        this.lastChecked = timestamp;
    }

    formatHistoryTime(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const diff = now - date;
        const days = Math.floor(diff / (1000 * 60 * 60 * 24));
        const hours = Math.floor(diff / (1000 * 60 * 60));
        const minutes = Math.floor(diff / (1000 * 60));

        if (days > 0) return `${days}d ago`;
        if (hours > 0) return `${hours}h ago`;
        return `${minutes}m ago`;
    }
} 