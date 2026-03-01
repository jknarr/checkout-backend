package com.paze.checkout.seed;

import com.paze.checkout.domain.*;
import com.paze.checkout.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CardRepository cardRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final BCryptPasswordEncoder bcrypt;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("DataSeeder: data already present, skipping");
            return;
        }

        // User 1: Jane Doe
        User jane = userRepository.save(User.builder()
                .phoneNumber("+15551234567")
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .build());

        cardRepository.save(Card.builder()
                .user(jane).last4("4242").expirationDate("12/27")
                .cardType("Visa").cardArtUrl("/card-art/visa-blue.svg")
                .cvvHash(bcrypt.encode("123")).build());

        cardRepository.save(Card.builder()
                .user(jane).last4("5555").expirationDate("08/26")
                .cardType("Mastercard").cardArtUrl("/card-art/mc-gold.svg")
                .cvvHash(bcrypt.encode("456")).build());

        shippingAddressRepository.save(ShippingAddress.builder()
                .user(jane).label("Home").firstName("Jane").lastName("Doe")
                .address("123 Main St").city("San Francisco").state("CA")
                .zip("94102").country("US").isDefault(true).build());

        shippingAddressRepository.save(ShippingAddress.builder()
                .user(jane).label("Work").firstName("Jane").lastName("Doe")
                .address("456 Market St").city("San Francisco").state("CA")
                .zip("94105").country("US").isDefault(false).build());

        // User 2: John Smith
        User john = userRepository.save(User.builder()
                .phoneNumber("+15559876543")
                .firstName("John")
                .lastName("Smith")
                .email("john@example.com")
                .build());

        cardRepository.save(Card.builder()
                .user(john).last4("0005").expirationDate("03/28")
                .cardType("Amex").cardArtUrl("/card-art/amex-green.svg")
                .cvvHash(bcrypt.encode("7890")).build());

        shippingAddressRepository.save(ShippingAddress.builder()
                .user(john).label("Home").firstName("John").lastName("Smith")
                .address("789 Oak Ave").city("New York").state("NY")
                .zip("10001").country("US").isDefault(true).build());

        log.info("DataSeeder: seeded 2 users, 3 cards, 3 addresses");
    }
}
