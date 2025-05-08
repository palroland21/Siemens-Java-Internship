package com.siemens.internship;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ItemService {
    @Autowired
    private ItemRepository itemRepository;
    private static ExecutorService executor = Executors.newFixedThreadPool(10);
    private List<Item> processedItems = Collections.synchronizedList(new ArrayList<>());
    private AtomicInteger processedCount = new AtomicInteger(0);


    public List<Item> findAll() {
        return itemRepository.findAll();
    }

    public boolean existsById(Long id) {
        return itemRepository.existsById(id);
    }

    public Optional<Item> findById(Long id) {
        return itemRepository.findById(id);
    }

    public Item save(Item item) {
        return itemRepository.save(item);
    }

    public void deleteById(Long id) {
        itemRepository.deleteById(id);
    }

    public int getProcessedCount() {
        return processedCount.get();
    }

    public List<Item> getProcessedItems() {
        return new ArrayList<>(processedItems);  // return a copy for safety
    }

    /**
     * Your Tasks
     * Identify all concurrency and asynchronous programming issues in the code
     * Fix the implementation to ensure:
     * All items are properly processed before the CompletableFuture completes
     * Thread safety for all shared state
     * Proper error handling and propagation
     * Efficient use of system resources
     * Correct use of Spring's @Async annotation
     * Add appropriate comments explaining your changes and why they fix the issues
     * Write a brief explanation of what was wrong with the original implementation
     *
     * Hints
     * Consider how CompletableFuture composition can help coordinate multiple async operations
     * Think about appropriate thread-safe collections
     * Examine how errors are handled and propagated
     * Consider the interaction between Spring's @Async and CompletableFuture
     */

    /**
     * Orignal code:
     *     public List<Item> processItemsAsync() {
     *
     *         List<Long> itemIds = itemRepository.findAllIds();
     *
     *         for (Long id : itemIds) {
     *             CompletableFuture.runAsync(() -> {
     *                 try {
     *                     Thread.sleep(100);
     *
     *                     Item item = itemRepository.findById(id).orElse(null);
     *                     if (item == null) {
     *                         return;
     *                     }
     *
     *                     processedCount++;
     *
     *                     item.setStatus("PROCESSED");
     *                     itemRepository.save(item);
     *                     processedItems.add(item);
     *
     *                 } catch (InterruptedException e) {
     *                     System.out.println("Error: " + e.getMessage());
     *                 }
     *             }, executor);
     *         }
     *
     *         return processedItems;
     *     }
     *
     * Errors & Fixes:
     *      Original: Returned 'processedItems' immediately without waiting for async operations to complete
     *      Fixed: Used CompletableFuture.allOf() + thenApply() to ensure all processing finishes before returning the results
     *
     *      Original: processedCount++ (int) => race condition and non-thread-safe processedItems acces
     *      Fixed: Used AtomicInteger for couting and synchronizedList for thread-safe collection
     *
     *      Original: Exception handling was missing
     *      Fixed: Used CompletionException for errors
     *
     *      Original: No way to know which items processed succesfully
     *      Fixed: Now collects and returns only succesful results (future.get())
     *
     *      Original: InterruptedException mishandled
     *      Fixed: Maintaining interrupt status via Thread.currentThread().interrupt() function
     *
     */
    @Async
    public CompletableFuture<List<Item>> processItemsAsync() {

        List<Long> itemIds = itemRepository.findAllIds();

        // Process each item asynchronously using a thread pool (executor)
        // Create a list of CompletableFuture<Item>
        List<CompletableFuture<Item>> processingFutures = itemIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> {
                    try{
                        // Find the item by ID or throw exception if not found
                        Item item = itemRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Item not found: " + id));

                        // Update the item status
                        item.setStatus("PROCESSED");

                        // Save the update item
                        Item savedItem = itemRepository.save(item);

                        processedItems.add(savedItem); // add to synchronized list
                        processedCount.incrementAndGet(); // atomically increment counter

                        return savedItem;
                    }catch(Exception e){
                        // Wrap any exceptions in CompletionException, without wrapping, exceptions could lose context
                        throw new CompletionException(new RuntimeException("Error processing item:" + id, e));
                    }
                        }, executor))
                .collect(Collectors.toList());

        // --> Combine all futures into a single future that completes when all are done

        // Waits for all threads to terminate
        CompletableFuture<Void> allDone = CompletableFuture.allOf(processingFutures.toArray(new CompletableFuture[0]));

        // After all tasks complete, collect results
        return allDone.thenApply(v -> {
            List<Item> results = new ArrayList<>();

            // Iterate through each future to get the results
            for(CompletableFuture<Item> future : processingFutures){
                try{
                    // Get the result
                    results.add(future.get());
                }catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted", e);
                }catch(ExecutionException e){
                    // Unwrap and throw the original exception
                    throw new RuntimeException("Error in processing future", e.getCause());
                }
            }
            return results;
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}

