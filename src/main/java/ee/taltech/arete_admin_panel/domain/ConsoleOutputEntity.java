package ee.taltech.arete_admin_panel.domain;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;

import javax.persistence.*;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "console_output")
@Entity
@JsonClassDescription("Stderr or Stdout")
public class ConsoleOutputEntity {

    @JsonPropertyDescription("Std message")
    @Column(columnDefinition = "TEXT")
    String content;

    @Column(name = "console_output_id")
    @JsonIgnore
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

}
