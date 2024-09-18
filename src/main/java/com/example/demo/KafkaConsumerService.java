package com.example.demo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

	@Autowired
	ExecutorService service;

	@Autowired
	KafkaConsumer<String, String> consumer;

	@Autowired
	DeadLetterLogRepository deadLetterLogRepository;
	@Autowired
	KafkaTemplate<String, String> kafkaTemplate;

	@Value("${kafka.poll.duration}") // Inject poll duration from configuration
	private long pollDuration;
	@Value("${kafka.consumer.paused}")
	private boolean isPaused;
	@Value("${kafka.consumer.pauseduration}")
	private int pauseDuration;
	@Autowired
	private Environment environment;

	@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES) // Check every 5 minutes
	public void refreshConsumerState() {
		// Load the new value of 'isPaused' dynamically from the config
		isPaused = environment.getProperty("kafka.consumer.paused", Boolean.class, false);
	}

	public void listen() {
		while (true) {
			if (isPaused) {
				if (!consumer.paused().containsAll(consumer.assignment())) {
				System.out.println("Consumer is paused. Waiting...");
				consumer.pause(consumer.assignment()); // Temporarily stops fetching records from the specified partition.
				}
				try {
					Thread.sleep(pauseDuration);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				continue; // Skip this iteration and recheck the paused status
			} else {
				consumer.resume(consumer.assignment()); // Resume the Kafka consumer when not paused
			}
			// Poll messages in batch
			ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollDuration)); // Poll messages
																										// every 1
																										// second

			// List to store all CompletableFutures for current batch
			List<CompletableFuture<Void>> futures = new ArrayList<>();

			// Process each record asynchronously
			for (ConsumerRecord<String, String> record : records) {
				CompletableFuture<Void> future = CompletableFuture.runAsync(() -> processMessage(record), service)
						.exceptionally(ex -> {
							System.out.println("Processing failed, retrying once...");
							retryMessage(record); // Retry once if processing fails
							return null;
						});
				futures.add(future);
			}

			// After all messages are processed, commit the offsets
			CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

			allOf.thenRun(() -> {
				try {
					consumer.commitSync(); // Commit offset synchronously after all processing is done
					System.out.println("Offsets committed after all tasks are done.");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}).join(); // Ensure we block until all messages are processed and offsets are committed
		}
	}

	private void retryMessage(ConsumerRecord<String, String> record) {
		CompletableFuture.runAsync(() -> processMessage(record), service).exceptionally(ex -> {
			System.out.println("Retry failed after second attempt, sending to Dead Letter Queue.");
			sendToDeadLetterQueue(record);
			return null;
		}).join(); // Block until retry is complete

	}

	// Process the Kafka message
	private void processMessage(ConsumerRecord<String, String> record) {
		try {
			System.out.println("Message processed: " + record.value());
			// Message processing
		} catch (Exception e) {

			throw new RuntimeException("Processing failed", e); // Force retry logic

		}
	}

	// Send failed messages to Dead Letter Queue. Can be kept on ddl for some
	// duration and again sent back to my-topic
	private void sendToDeadLetterQueue(ConsumerRecord<String, String> record) {
		// Save to db
		DeadLetterLog logEntry = new DeadLetterLog("dead-letter-topic", record.key(), record.value(),
				LocalDateTime.now());
		deadLetterLogRepository.save(logEntry);
//  Can also be sent to dead letter queue for sending message back to main topic after delay or any other logic.
		kafkaTemplate.send(new ProducerRecord<String, String>("dead-letter-topic", record.key(), record.value()));
		System.out.println("Sent to Dead Letter Queue: " + record.value());
	}
}
