package vn.edu.ute.milktea.entity.account;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="AccountCart") @Getter @Setter @NoArgsConstructor
public class AccountCart {
    @Id @Column(name="AccountId") private Long accountId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="AccountId",insertable=false,updatable=false) private Account account;
    @Column(name="ItemsJson",nullable=false,columnDefinition="text") private String itemsJson="[]";
    @Column(name="Revision",nullable=false) private long revision;
}
