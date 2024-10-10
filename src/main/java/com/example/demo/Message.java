package com.example.demo;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Message {

    @Id
    private Long key;
    private String topic;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private boolean isProcessed = false;

    public boolean isProcessed() {
		return isProcessed;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getProcessedAt() {
		return processedAt;
	}

	public void setProcessedAt(LocalDateTime processedAt) {
		this.processedAt = processedAt;
	}

	public void setProcessed(boolean isProcessed) {
		this.isProcessed = isProcessed;
	}

	public Message() {}

    public Message(String topic, Long key, String message, LocalDateTime createdAt) {
        this.topic = topic;
        this.key = key;
        this.message = message;
        this.createdAt = createdAt;
    }
    
    public Message(  Long key, String topic, String message, LocalDateTime processedAt) {
        this.topic = topic;
        this.key = key;
        this.message = message;
        this.processedAt = processedAt;
    }
    
    public Message( String topic, Long key, String message, LocalDateTime processedAt, boolean isProcessed) {
        this.topic = topic;
        this.key = key;
        this.message = message;
        this.processedAt = processedAt;
        this.isProcessed = true;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Long getKey() {
        return key;
    }

    public void setKey(Long key) {
        this.key = key;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}

