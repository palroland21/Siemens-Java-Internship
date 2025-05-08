package com.siemens.internship;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
@Validated
@RequestMapping("/api/items")
public class ItemController {

    @Autowired
    private ItemService itemService;

    /**
     * Get all items from database
     * @return ResponseEntity with the list of all items
     */
    @GetMapping
    public ResponseEntity<List<Item>> getAllItems() {
        return new ResponseEntity<>(itemService.findAll(), HttpStatus.OK);
    }

    /**
     * Create a new item
     * @param item  -> Item object
     * @param result -> valudation result
     * @return ResponseEntity with created item or validation errors
     */
    @PostMapping
    public ResponseEntity<?> createItem(@Valid @RequestBody Item item, BindingResult result) {
        if (result.hasErrors()) {
            String errorMsg = result.getFieldErrors().stream()
                    .filter(error -> error.getField().equals("email"))
                    .findFirst()
                    .map(error -> error.getDefaultMessage())
                    .orElse("Invalid fields: " +
                            (result.getFieldErrors().isEmpty() ? "unknown" :
                             result.getFieldErrors().get(0).getField())
                    );
            return new ResponseEntity<>(errorMsg, HttpStatus.BAD_REQUEST);  // Changed from HttpStatus.CREATED to BAD_REQUEST | Code: 400 for BAD_REQUEST
        }
        return new ResponseEntity<>(itemService.save(item), HttpStatus.CREATED); // Changed from HttpStatus.BAD_REQUEST to CREATED | Code: 201 for CREATED
    }

    /**
     * Get item by ID
     * @param id -> ID of the item
     * @return ResponseEntity with the item if it was found or empty response if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Item> getItemById(@PathVariable Long id) {
        return itemService.findById(id)
                .map(item -> new ResponseEntity<>(item, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND)); // Changed from NO_CONTENT(Code: 204)
                                                                    // to NOT_FOUND to indicate that the requested resource was not found | Code: 404 for NOT_FOUND
    }

    /**
     * Update existing item
     * @param id -> ID of the item
     * @param item -> Updated item data
     * @return ResponseEntity with updated item (status 200) or empty response (status 404)
     */
    @PutMapping("/{id}")
    public ResponseEntity<Item> updateItem(@PathVariable Long id, @Valid @RequestBody Item item) {
        Optional<Item> existingItem = itemService.findById(id);
        if (existingItem.isPresent()) {
            item.setId(id);
            return new ResponseEntity<>(itemService.save(item), HttpStatus.OK); // Changed from CREATED to OK | Code: 200 for OK
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // Changed from ACCEPTED (Code: 202) to NOT_FOUND (Code: 404) when resource was not found
        }
    }

    /**
     * Delete item by ID
     * @param id -> ID of the item
     * @return ResponseEntity with no content if successfully deleted or 'not found' if the item doesn't exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        if(itemService.existsById(id)) {
            itemService.deleteById(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT); // Successfully deleted | Code: 204
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // Code: 404
        }
    }

    /**
     * Process all items asynchronously
     * @return CompletableFuture containing either:
     *              - ResponseEntity with list of processed items, or
     *              - ResponseEntity with error status (Internal Server Error: 500) if processing fails
     */
    @GetMapping("/process")
    public CompletableFuture<ResponseEntity<List<Item>>> processItems() {  // CompletableFutures<> wrapper makes this operation asynchronous (does not block threads)
        // Problem: processItemsAsync() is asynchronous, but it's treated as synchronous here because we don't wait for completion.
        // This may return results before processing finishes.
        // * return new ResponseEntity<>(itemService.processItemsAsync(), HttpStatus.OK);
        return itemService.processItemsAsync()
                .thenApply(items -> ResponseEntity.ok(items))
                .exceptionally(ex -> ResponseEntity.status(500).build()); // Code: 500 is Internal Server Error

    }
}
