# Mini Banking API

This is a small REST API for opening bank accounts and transferring money between them.

Stack - Java 21, Spring Boot, Spring Data JPA, Gradle and H2 database.


## Questions

1. How did you design the API?

I kept account operations under `/api/v1/accounts`, `POST` open an account, `GET` reads accounts, `PATCH` updates only the owner name and `DELETE` closes an account,  transfers use `/api/v1/transactions` and history is under `/accounts/{id}/transactions`. I used `201` for created resources, `204` for a successful close, `400` for invalid input, `404` for a missing account, `409` for a conflict and `422` when a valid transfer is rejected by a business rule.

2. How did you guarantee that a transfer is atomic?

I applied `@Transactional` to `TransactionService.transfer()`, I placed @Transactional on the service method because a transfer contains several database changes, and they must all succeed or fail together.

3. What happened when an error occurred after the debit but before the credit?

I tested this by temporarily flushing the source account immediately after debiting it and then throwing an exception before crediting the destination. Hibernate executed the balance update and the request returned `500`, but later GET requests showed the original balances of `1000.00` and `0.00`. Since the service method runs inside one `@Transactional` boundary, the exception rolled the database update back.

4. How did you protect the system against repeated requests?

Every transfer requires an `Idempotency-Key`, which is stored on a successful transaction and has a unique database constraint. Before changing any balance, the service checks the key and returns the stored transaction when the request parameters match, while using the same key with different parameters returns `409`. `saveAndFlush()` also makes the database detect a duplicate key before the transaction finishes, preventing a second debit.

5. What could go wrong with two transfers on the same account?

Two transfers could read the same balance before either one commits, so both could pass the balance check and cause a lost update or allow overspending. 
Now we does not lock account rows and I listed this limitation in not finished block below. I would solve it by
loading both accounts with a PESSIMISTIC_WRITE write lock in account repository but I did not have enough time to implement and test it.

6. Why did you use this project structure?

Controllers handle HTTP, DTOs define request and response data, services contain business rules and transaction boundaries, repositories access the database, entities represent stored data and mappers keep conversion code out of services. 
This gives the small project enough separation.
If it grew much larger, my first change would be to group code by feature and split TransactionService: 
TransferService would handle balance changes and idempotency, while other some service would handle history and top-account queries. 
I would also replace the in memory H2 create-drop setup with a persistent database and versioned Flyway migrations.

7. What assumptions did you make?

Deleting an account closes it instead of removing it, because its transaction history should remain and closed accounts are still visible, but cannot be used for new transfers.

An account can be opened with a zero balance, but not a negative balance. There are no separate deposit or withdrawal operations.

The supported currencies are GEL, USD and EUR. Currency cannot be changed and transfers between different currencies are rejected.

Money can have up to two decimal places. Longer values are rejected instead of rounded.

Database ids use generated Long values. Account numbers are created by the server with a GE prefix and a random part taken from a UUID.

Every transfer requires an Idempotency-Key. 

History includes both sent and received transactions and shows the newest first,pages start from zero and the maximum page size is 100.

Top accounts are calculated using successful transactions, and both accounts involved in a transfer are counted.

All errors use the same ProblemDetail format with an error code, so the client can understand what failed.


## Not finished

Failed transfer attempts return clear error responses, but they aren't saved as failed rows in the transactions table.

Idempotency stores successful transfer results. A rejected request is not stored, and if two requests with the same key arrive at exactly the same time, one of them may return `409` instead of replaying the first response. 
The database unique constraint still prevents a second successful debit.

I checked the account, transfer, idempotency, history and rollback scenarios manually, but I did not finish automated integration tests.

The optional Swagger, Docker, daily limit and request/response logging features were not implemented because of time.




## Build and run

Java 21 is required.

```
./gradlew build
./gradlew bootRun
```

The API runs on `http://localhost:8081`.

The H2 console is available at `http://localhost:8081/h2-console`.

- JDBC URL: `jdbc:h2:mem:minibanking`
- User: `sa`
- Password: empty

The database is h2 and Hibernate uses `create-drop`, so all data is removed when the application stops.

## Testing with Postman

All endpoints start with `http://localhost:8081/api/v1`.

For POST and PATCH requests I used Postman's Body > raw > JSON option. 

### Open an account

- Method: `POST`
- URL: `http://localhost:8081/api/v1/accounts`

Body:

json

{
  "ownerName": "Luka Nizharadze",
  "initialBalance": 1000.00,
  "currency": "GEL"
}

The response is `201 Created`. The account number is generated by the application. 

I created a second account for transactions with the same request and this body:


{
  "ownerName": "Nika Beridze",
  "initialBalance": 0.00,
  "currency": "GEL"
}


The examples assume the second account has id 2.

Supported currencies are `GEL`, `USD` and `EUR`. 
Amounts with more than two decimal places are rejected.

### List all accounts

- Method: `GET`
- URL: `http://localhost:8081/api/v1/accounts`

The response contains active and closed accounts.

### Get one account

- Method: `GET`
- URL: `http://localhost:8081/api/v1/accounts/1`

A missing account returns 404 with the code `ACCOUNT_NOT_FOUND`.

### Update the owner name

- Method: `PATCH`
- URL: `http://localhost:8081/api/v1/accounts/1`

Body:

json
{
  "ownerName": "Luka N"
}

Only the owner name can be changed by this operation. Balance and currency are not part of the request.

### Close an account

An account can be closed only when its balance is zero. 

- Method: `DELETE`
- URL: `http://localhost:8081/api/v1/accounts/3`

The response is 204 no contet. The row is not deleted, its status becomes CLOSED. Repeating the same DELETE request also returns 204.

Trying to close an account that still holds money returns 409 with the code "ACCOUNT_NOT_EMPTY".

### Transfer money

- Method: `POST`
- URL: `http://localhost:8081/api/v1/transactions`

Header:


Idempotency-Key: transfer-001


Body:

json
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 200.00
}


The response is `201 Created` with a `SUCCESS` transaction. The source balance becomes `800.00` and the destination balance becomes `200.00`.

To test idempotency I sent the same request again with the same key. The API returned the first transaction with the same id and the balances didnot change again.

I also kept the key `transfer-001` and changed the amount to `500.00`. The API returned 409 with `IDEMPOTENCY_KEY_CONFLICT`. A request without the header returned 400 with `IDEMPOTENCY_KEY_REQUIRED`.

### Account transaction history

- Method: `GET`
- URL: `http://localhost:8081/api/v1/accounts/1/transactions`

In Postman's Params tab I used:


page = 0
size = 20


The response contains incoming and outgoing transactions ordered from newest to oldest. It also returns the current page, page size, total number of transactions and total pages.

The date filter is optional. I tested it with these Params:


from = 2026-08-01T00:00:00Z
to = 2026-09-01T00:00:00Z
page = 0
size = 20


`from` is included and `to` is excluded. The maximum page size is `100`. History for an account that does not exist returns `404 ACCOUNT_NOT_FOUND`.

### Top five accounts

- Method: `GET`
- URL: `http://localhost:8081/api/v1/accounts/top`

The response contains up to five accounts ordered by the number of successful transactions. A transaction is counted for both the sender and the receiver because both accounts took part in it.

## Error responses

Errors use Spring `ProblemDetail` and include a `code` property so the client can distinguish the reason.

Invalid input, zero or negative amounts and same-account transfers return `400`.
A missing account returns `404`.
Closing an account that still has money and reusing an idempotency key with different parameters return `409`.
Insufficient funds, closed accounts and currency mismatch return `422`.

