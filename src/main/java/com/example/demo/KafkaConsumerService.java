package com.example.demo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
	KafkaConsumer<Long, String> consumer;

	@Autowired
	MessageRepository messageRepository;
	@Autowired
	KafkaTemplate<Long, String> kafkaTemplate;

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
		consumer.subscribe(List.of("my-topic"));
		// List to store all CompletableFutures for current batch
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		while (true) {
			if (isPaused) {
				if (!consumer.paused().containsAll(consumer.assignment())) {
					System.out.println("Consumer is paused. Waiting...");
					consumer.pause(consumer.assignment()); // Temporarily stops fetching records from the specified
															// partition.
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
			ConsumerRecords<Long, String> records = consumer.poll(Duration.ofMillis(pollDuration)); // Wait for the
																										// pollDuration
																										// to get the
				
		
			//Save messages first in db
			if (records != null) {
				for (ConsumerRecord<Long, String> record : records) {
					saveNewMessage(record);
				}
				//commit offset before processing
				try {
					consumer.commitSync(); // Commit offset synchronously even before processing is done
					System.out.println("Offsets committed after all tasks are done.");
				} catch (Exception e) {
					e.printStackTrace();
				}

				// Process each record asynchronously
				for (ConsumerRecord<Long, String> record : records) {
				
					CompletableFuture<Void> future = CompletableFuture.runAsync(() -> processMessage(record), service)
							.handle((result, ex) -> {
								if (ex != null) {
									System.out.println("Processing failed, retrying once..."+ ex.getMessage());
									retryMessage(record); // Handle retry logic
								}
								return null;
							});
					futures.add(future);
				}

				
				// Check for completion of futures, remove completed ones
				futures.removeIf(CompletableFuture::isDone);
			}

			try {
				System.out.println("About to sleep for 10 mins");
				// Main thread to sleep for 10 mins and then poll again for messages.
				Thread.sleep(100000); // Sleep for 10 minutes
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt(); // Restore the interrupted status
				System.out.println("Sleep interrupted. Exiting...");
				break; // Optionally exit or handle it as needed
			}
		}
	}

	private Message saveNewMessage(ConsumerRecord<Long, String> record) {
		Message message = new Message("my-topic", record.key(), record.value(),
				LocalDateTime.now());
		Message message1= messageRepository.save(message);
		return message1;
	}

	private void retryMessage(ConsumerRecord<Long, String> record) {
		//service.shutdownNow();
		CompletableFuture.runAsync(() -> processMessage(record), service).exceptionally(ex -> {
			ex.printStackTrace();
			System.out.println("Retry failed after second attempt, sending to Dead Letter Queue."+ ex.getMessage());
			sendToDeadLetterQueue(record);
			//service.shutdown();
			return null;
		}).join(); // Block until retry is complete

	}

	// Process the Kafka message
	private void processMessage(ConsumerRecord<Long, String> record) {
		try {
			System.out.println("Message recieved: " + record.value());
			//simulating error processing message..
			if(record.value().contains("error")) {
				throw new Exception("Message failed to process");
			}
			Thread.sleep(60000); // Sleep for 10 minutes, assuming processing takes 10 mins.
			System.out.println("Processing logging into db "+ record.key());
			 Optional<Message> logEntry = messageRepository.findById(record.key());
			 Message updateMessage = null;
			 if(logEntry.isEmpty()) {
				 updateMessage = saveNewMessage(record);
			 }else {
				 updateMessage = logEntry.get();
			 }
			 updateMessage.setProcessed(true);
			 updateMessage.setProcessedAt(LocalDateTime.now());
			 messageRepository.save(updateMessage);
			 System.out.println("Message processed: " + record.value());
			// Message processing
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt(); // Restore the interrupted status
			System.out.println("Sleep interrupted. Exiting...");
			throw new RuntimeException("Processing failed", e); // Force retry logic
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Processing failed", e); // Force retry logic

		}
	}

	// Send failed messages to Dead Letter Queue. Can be kept on ddl for some
	// duration and again sent back to my-topic
	private void sendToDeadLetterQueue(ConsumerRecord<Long, String> record) {
		// Save to db
	//	Message logEntry = new Message(Long.parseLong(record.key()) , "dead-letter-topic", record.key(), record.value(),LocalDateTime.now());
	//	messageRepository.save(logEntry);
		
		 Optional<Message> logEntry = messageRepository.findById(record.key());
		 Message updateMessage = logEntry.get();
		 updateMessage.setTopic("dead-letter-topic");
		 messageRepository.save(updateMessage);
//  Can also be sent to dead letter queue for sending message back to main topic after delay or any other logic.
		kafkaTemplate.send(new ProducerRecord<Long, String>("dead-letter-topic", record.key(), record.value()));
		System.out.println("Sent to Dead Letter Queue: " + record.value());
	}
}
