package sa.lendo.credit.analyst.multi_agent.mock;

import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory demo applicants, each engineered to exercise a different decision path.
 *
 * Policy rules: score >= 680, age >= 21, DTI < 40%.
 * Car loan guide: LTV <= 90%, min income $40k. Personal loan guide: score >= 700.
 */
@Repository
public class ApplicantProfileRepository {

    private final Map<String, ApplicantProfile> profiles = new LinkedHashMap<>();

    public ApplicantProfileRepository() {
        add(new ApplicantProfile("APP-001", "Sara Green", 30, 745, 95000, 15000, 20000, 25000,
                "Strong profile - expected clean approval (DTI 15.8%, LTV 80%)"));
        add(new ApplicantProfile("APP-002", "John Doe", 28, 690, 85000, 25000, 27000, 29000,
                "Car loan fails LTV cap (93.1% > 90%); personal loan fails score floor (690 < 700)"));
        add(new ApplicantProfile("APP-003", "Omar Haddad", 35, 720, 60000, 30000, 15000, 20000,
                "High debt burden - fails general DTI policy (50% >= 40%)"));
        add(new ApplicantProfile("APP-004", "Lena Torres", 26, 650, 70000, 10000, 10000, 15000,
                "Credit score 650 below the general 680 floor - rejected for any product"));
        add(new ApplicantProfile("APP-005", "Adam Nassar", 24, 700, 80000, 31200, 22500, 25000,
                "Borderline case - score exactly 700, DTI 39%, LTV exactly 90%"));
    }

    private void add(ApplicantProfile profile) {
        profiles.put(profile.id(), profile);
    }

    public List<ApplicantProfile> findAll() {
        return List.copyOf(profiles.values());
    }

    public Optional<ApplicantProfile> findById(String id) {
        return Optional.ofNullable(profiles.get(id));
    }
}
