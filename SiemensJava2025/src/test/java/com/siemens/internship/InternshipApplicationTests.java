package com.siemens.internship;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import javax.naming.Binding;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InternshipApplicationTests {

	@Autowired
	private ItemController itemController;
	@Autowired
	private ItemService itemService;

	@Test
	void contextLoads() {
		assertNotNull(itemController);
		assertNotNull(itemService);
	}

	@Test
	void testCreateItemWithValidData(){
		Item item = new Item();
		item.setName("Test Item");
		item.setDescription("Test Description");
		item.setStatus("NEW");
		item.setEmail("email_valid@gmail.com");

		BindingResult result = new BeanPropertyBindingResult(item, "item");
		ResponseEntity<?> response = itemController.createItem(item, result);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody() instanceof Item);
	}

	@Test
	void testCreateItemWithInvalidData(){
		Item item = new Item();
		item.setName("Test Item");
		item.setDescription("Test Description");
		item.setStatus("NEW");
		item.setEmail("email_invalid");

		BindingResult result = new BeanPropertyBindingResult(item, "item");
		result.rejectValue("email", "invalid_email", "Invalid email! Valid email format is: user@gexample.com");

		ResponseEntity<?> response = itemController.createItem(item, result);
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals("Invalid email! Valid email format is: user@gexample.com", response.getBody().toString());
	}

	@Test
	void testGetItemById(){
		Item item = new Item();
		item.setName("Test GetItem");
		item.setDescription("Test Description");
		item.setStatus("NEW");
		item.setEmail("email_valid@gmail.com");

		BindingResult result = new BeanPropertyBindingResult(item, "item");
		Item createdItem = (Item) itemController.createItem(item, result).getBody();

		ResponseEntity<?> response = itemController.getItemById(createdItem.getId());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(createdItem.getId(), ((Item) response.getBody()).getId());
	}

	@Test
	void testGetNonExistingItemById(){
		ResponseEntity<?> response = itemController.getItemById(9999L);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void testUpdateItem(){
		Item item = new Item();
		item.setName("Test UpdateItem");
		item.setDescription("Test Description");
		item.setStatus("NEW");
		item.setEmail("update_valid_email@gmail.com");

		BindingResult result = new BeanPropertyBindingResult(item, "item");
		Item createdItem = (Item) itemController.createItem(item, result).getBody();

		createdItem.setName("Updated Name");
		ResponseEntity<?> updatedResponse = itemController.updateItem(createdItem.getId(), createdItem);
		assertEquals(HttpStatus.OK, updatedResponse.getStatusCode());
		assertEquals("Updated Name", ((Item) updatedResponse.getBody()).getName());
	}

	@Test
	void testUpdateNonExistingItem(){
		Item item = new Item();
		item.setName("Doen't exist");
		ResponseEntity<?> response = itemController.updateItem(9999L, item);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void testDeleteItem(){
		Item item = new Item();
		item.setName("Test DeleteItem");
		item.setDescription("Test Description");
		item.setStatus("NEW");
		item.setEmail("delete_valid_email@gmail.com");

		BindingResult result = new BeanPropertyBindingResult(item, "item");
		Item createdItem = (Item) itemController.createItem(item, result).getBody();
		// Delete the item
		ResponseEntity<Void> response = itemController.deleteItem(createdItem.getId());
		assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

		//Verify that it is deleted
		ResponseEntity<Item> getResponse = itemController.getItemById(createdItem.getId());
		assertEquals(HttpStatus.NOT_FOUND, getResponse.getStatusCode());
	}


	@Test
	void testDeleteNonExistingItem(){
		ResponseEntity<?> response = itemController.deleteItem(9999L);
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void testGetAllItems(){
		Item item1 = new Item();
		item1.setName("Test Name Item1");
		item1.setDescription("Test Description Item1");
		item1.setStatus("NEW");
		item1.setEmail("email_valid_item1@gmail.com");

		BindingResult result1 = new BeanPropertyBindingResult(item1, "item1");
		itemController.createItem(item1, result1);

		Item item2 = new Item();
		item2.setName("Test Name Item2");
		item2.setDescription("Test Description Item2");
		item2.setStatus("NEW");
		item2.setEmail("email_valid_item2@gmail.com");

		BindingResult result2 = new BeanPropertyBindingResult(item2, "item2");
		itemController.createItem(item2, result2);

		// Test getting all items
		ResponseEntity<List<Item>> response = itemController.getAllItems();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().size() >= 2);
	}

	@Test
	void testProcessItemsAsync() throws ExecutionException, InterruptedException {
		Item item1 = new Item();
		item1.setName("Test Name Item1");
		item1.setDescription("Test Description Item1");
		item1.setStatus("NEW");
		item1.setEmail("email_valid_item1@gmail.com");

		BindingResult result1 = new BeanPropertyBindingResult(item1, "item1");
		itemController.createItem(item1, result1);

		Item item2 = new Item();
		item2.setName("Test Name Item2");
		item2.setDescription("Test Description Item2");
		item2.setStatus("NEW");
		item2.setEmail("email_valid_item2@gmail.com");

		BindingResult result2 = new BeanPropertyBindingResult(item2, "item2");
		itemController.createItem(item2, result2);

		// Process items
		CompletableFuture<ResponseEntity<List<Item>>> future = itemController.processItems();
		ResponseEntity<List<Item>> response = future.get();

		// Verify processing
		assertEquals(HttpStatus.OK, response.getStatusCode());
		List<Item> processedItems = response.getBody();
		assertNotNull(processedItems);
		assertTrue(processedItems.size() >= 2);
		processedItems.forEach(item -> assertEquals("PROCESSED", item.getStatus()));

		// Verify processed count
		assertTrue(itemService.getProcessedCount() >= 2);
	}

	@Test
	void testProcessItemsAsyncWithEmptyDatabase() throws ExecutionException, InterruptedException {
		// Clear existing items
		itemService.findAll().forEach(item -> itemService.deleteById(item.getId()));

		// Process items
		CompletableFuture<ResponseEntity<List<Item>>> future = itemController.processItems();
		ResponseEntity<List<Item>> response = future.get();

		//Verify processing
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().isEmpty());
		assertEquals(0, itemService.getProcessedCount());
	}
}
