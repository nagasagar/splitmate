package com.splitmate.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /** Primary key is the Supabase user UUID (JWT 'sub' claim). */
    @Id
    @Column(name = "supabase_user_id")
    private String supabaseUserId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;
}
