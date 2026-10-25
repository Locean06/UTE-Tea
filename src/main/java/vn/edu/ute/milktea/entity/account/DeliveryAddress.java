package vn.edu.ute.milktea.entity.account;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="DeliveryAddress") @Getter @Setter @NoArgsConstructor
public class DeliveryAddress {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="Id") private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="AccountId") private Account account;
    @Column(name="Recipient",nullable=false,length=120) private String recipient;
    @Column(name="Phone",nullable=false,length=20) private String phone;
    @Column(name="AddressLine",nullable=false,length=500) private String addressLine;
    @Column(name="IsDefault",nullable=false) private boolean defaultAddress;
}
