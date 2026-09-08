package pe.edu.dentalcite.consultorio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "consultorios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Consultorio {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(nullable = false)
    private Boolean inoperativo;
}
