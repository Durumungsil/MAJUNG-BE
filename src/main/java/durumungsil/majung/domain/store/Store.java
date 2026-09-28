package durumungsil.majung.domain.store;

import jakarta.persistence.*;

@Entity
@Table(name = "stores")
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "destination_id")
    private Long destinationId;

    @Column(name = "destination_name")
    private String destinationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_type")
    private Type destinationType;

    private Double latitude;

    private Double longitude;

    private String sido;

    private String sigungu;

    private String address;

    private String branch;
}
