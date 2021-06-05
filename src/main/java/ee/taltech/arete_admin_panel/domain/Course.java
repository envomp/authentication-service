package ee.taltech.arete_admin_panel.domain;

import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Course {

    @NotNull
    private String gitUrl;

    @NotNull
    private String name; // Every git url has a namespace

    @NotNull
    @Builder.Default
    private Integer totalCommits = 0;

    @NotNull
    @Builder.Default
    private Integer totalTestsRan = 0;

    @NotNull
    @Builder.Default
    private Integer totalTestsPassed = 0;

    @NotNull
    @Builder.Default
    private Integer totalDiagnosticErrors = 0;

    @NotNull
    @Builder.Default
    private Integer totalTestErrors = 0;

    @NotNull
    @Builder.Default
    private Integer differentStudents = 0;

    @NotNull
    @Builder.Default
    private Integer commitsStyleOK = 0;
}
