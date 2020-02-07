package ee.taltech.arete_admin_panel.pojo.abi.users.student;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StudentTableDto {

    long id;

    String uniid;

    long firstTested;

    int totalCommits;

    int totalTestsRan;

    int totalTestsPassed;

    int totalDiagnosticErrors;

    int failedCommits;

    int commitsStyleOK;

}
