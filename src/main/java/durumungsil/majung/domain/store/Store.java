package durumungsil.majung.domain.store;

import jakarta.persistence.*;

@Entity
@Table(name = "stores")
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sourceId;

    private String name;

    @Enumerated(EnumType.STRING)
    private Type type;

    private Double latitude;

    private Double longitude;

    private String sido;

    private String sigungu;

    private String address;

    private String branch;
}
