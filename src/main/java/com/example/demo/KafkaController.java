/*
 * package com.example.demo;
 * 
 * import org.springframework.web.bind.annotation.PostMapping; import
 * org.springframework.web.bind.annotation.RequestBody; import
 * org.springframework.web.bind.annotation.RequestMapping; import
 * org.springframework.web.bind.annotation.RestController;
 * 
 * @RestController
 * 
 * @RequestMapping("/kafka") public class KafkaController {
 * 
 * private final KafkaProducerService kafkaProducerService;
 * 
 * public KafkaController(KafkaProducerService kafkaProducerService) {
 * this.kafkaProducerService = kafkaProducerService; }
 * 
 * @PostMapping("/send") public void sendMessage(@RequestBody String message) {
 * kafkaProducerService.sendMessage("my-topic", message); } }
 */