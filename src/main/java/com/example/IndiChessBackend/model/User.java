package com.example.IndiChessBackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.UniqueElements;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User  {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long userId;
    @Size(min = 4, max = 50,
            message = "Username must have characters between 4 and 50")

    @Column(name = "user_name", unique = true)
    String username;

    @Column(name = "email_id", unique = true)
    @Email
    String emailId;

    @Size(min=6,max=512)
    String password;

    String pfpUrl;

//    LocalDate dob;

    String country;

    Integer rating ;// default rating

}
