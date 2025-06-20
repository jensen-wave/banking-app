package net.javaguides.banking.service.impl;

import net.javaguides.banking.dto.AccountDto;
import net.javaguides.banking.dto.TransactionDTO;
import net.javaguides.banking.dto.TransferFundDTO;
import net.javaguides.banking.entity.Account;
import net.javaguides.banking.entity.Transaction;
import net.javaguides.banking.exception.AccountException;
import net.javaguides.banking.mapper.AccountMapper;
import net.javaguides.banking.repository.AccountRepository;
import net.javaguides.banking.repository.TransactionRepository;
import net.javaguides.banking.service.AccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Transactional
@Service
public class AccountServiceImpl implements AccountService {

    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;

    private static final String TRANSACTION_TYPE_DEPOSIT = "deposit";
    private static final String TRANSACTION_TYPE_WITHDRAW = "withdraw";
    private static final String TRANSACTION_TYPE_TRANSACTION = "transaction";

    public AccountServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }


    @Override
    public AccountDto createAccount(AccountDto accountDto) {
        Account account = AccountMapper.mapTOAccount(accountDto);
        Account saveAccount = accountRepository.save(account);
        AccountDto accountDto1 = AccountMapper.mapTOAccountDto(saveAccount);
        return accountDto1;
    }

    @Transactional(readOnly = true)
    @Override
    public AccountDto getAccountById(Long id) {
        Account account = accountRepository.findById(id).orElseThrow(() -> new AccountException("Account does not exist"));
        return AccountMapper.mapTOAccountDto(account);
    }

    @Override
    public AccountDto deposit(Long id, BigDecimal amount) {

        Account account = accountRepository.
                findById(id).orElseThrow(() -> new AccountException("Account does not exist"));

        account.setBalance(account.getBalance().add(amount));

        Account saveAccount = accountRepository.save(account);

        AccountDto accountDto = AccountMapper.mapTOAccountDto(saveAccount);

        // 記錄交易
        Transaction transaction = new Transaction();
        transaction.setAccountId(id);
        transaction.setAmount(amount);
        transaction.setTimestamp(LocalDateTime.now());
        transaction.setTransactionType(TRANSACTION_TYPE_DEPOSIT);
        transactionRepository.save(transaction);


        return accountDto;
    }

    @Override
    public AccountDto withdraw(Long id, BigDecimal amount) {
        Account account = accountRepository.findById(id).orElseThrow(() -> new AccountException("Account does not exist"));

        if (account.getBalance().compareTo(amount) < 0) {
            throw new AccountException("Insufficient amount");
        }


        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        AccountDto accountDto = AccountMapper.mapTOAccountDto(account);

        // 記錄交易
        Transaction transaction = new Transaction();
        transaction.setAccountId(id);
        transaction.setAmount(amount);
        transaction.setTimestamp(LocalDateTime.now());
        transaction.setTransactionType(TRANSACTION_TYPE_WITHDRAW);

        transactionRepository.save(transaction);

        return accountDto;
    }

    @Transactional(readOnly = true)
    @Override
    public Page<AccountDto> getAllAccounts(Pageable pageable) {

        Page<Account> accounts = accountRepository.findAll(pageable);

        Page<AccountDto> accountDtoPage = accounts.map(account -> AccountMapper.mapTOAccountDto(account));

        return accountDtoPage;
    }

    @Override
    public void deleteAccount(Long id) {
        Account account = accountRepository.findById(id).orElseThrow(() -> new AccountException("Account does not exist"));
        accountRepository.deleteById(id);
    }

    @Override
    public void transferFunds(TransferFundDTO transferFundDTO) {

        Long fromAccountId = transferFundDTO.fromAccountId();
        Long toAccountId = transferFundDTO.toAccountId();


        Account account1, account2;

        if (fromAccountId < toAccountId) {
            account1 = accountRepository.findById(fromAccountId).orElseThrow(() -> new AccountException("Account does not exist"));
            account2 = accountRepository.findById(toAccountId).orElseThrow(() -> new AccountException("Account does not exist"));
        } else {
            account2 = accountRepository.findById(toAccountId).orElseThrow(() -> new AccountException("Account does not exist"));
            account1 = accountRepository.findById(fromAccountId).orElseThrow(() -> new AccountException("Account does not exist"));
        }
        // 找出哪個是轉出帳戶，哪個是轉入帳戶

        Account fromAccount = account1.getId().equals(fromAccountId) ? account1 : account2;
        Account toAccount = account2.getId().equals(toAccountId) ? account2 : account1;


//        // 1. 檢索轉出帳戶
//        Account fromAccount = accountRepository.findById(transferFundDTO.fromAccountId()).orElseThrow(() -> new AccountException("Account does not exist"));
//        // 2. 檢索轉入帳戶
//         Account toAccount = accountRepository.findById(transferFundDTO.toAccountId()).orElseThrow(() -> new AccountException("Account does not exist"));


        if (fromAccount.getBalance().compareTo(transferFundDTO.amount()) < 0) {
            throw new AccountException("Insufficient amount");
        }

        // 3. 從轉出帳戶扣款
        fromAccount.setBalance(fromAccount.getBalance().subtract(transferFundDTO.amount()));
        // 4. 轉入帳戶存入金額
        toAccount.setBalance(toAccount.getBalance().add(transferFundDTO.amount()));
        // 5. 儲存更新
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // 記錄交易（僅記錄來源帳戶的轉出交易，目標帳戶可另行記錄）
        Transaction transaction = new Transaction();

        transaction.setAccountId(transferFundDTO.fromAccountId());
        transaction.setAmount(transferFundDTO.amount());
        transaction.setTimestamp(LocalDateTime.now());
        transaction.setTransactionType(TRANSACTION_TYPE_TRANSACTION);
        transactionRepository.save(transaction);
    }


    @Transactional(readOnly = true)
    @Override
    public Page<TransactionDTO> getAccountTransactions(Long accountId, Pageable pageable) {

        Page<Transaction> transactions = transactionRepository.findByAccountIdOrderByTimestampDesc(accountId, pageable);
        Page<TransactionDTO> transactionDTOPage = transactions.map(this::convertEntityToDTO);
//        List<TransactionDTO> transactionDTOList = new ArrayList<>();
//
//        for (Transaction transaction : transactionList) {
//            TransactionDTO transactionDTO = convertEntityToDTO(transaction);
//            transactionDTOList.add(transactionDTO);
//        }

//        List<TransactionDTO> collect =
//                transactions.stream().
//                map(transaction -> convertEntityToDTO(transaction)).
//                collect(Collectors.toList());


        return transactionDTOPage;
    }


    private TransactionDTO convertEntityToDTO(Transaction transaction) {
        return new TransactionDTO(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getAmount(),
                transaction.getTransactionType(),
                transaction.getTimestamp());
    }


}
