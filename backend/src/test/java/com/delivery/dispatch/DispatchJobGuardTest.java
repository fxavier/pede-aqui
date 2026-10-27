package com.delivery.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.common.exception.BusinessException;
import com.delivery.dispatch.entity.DispatchJob;
import com.delivery.dispatch.entity.DispatchJobStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** State guards on DispatchJob transitions (INC-03, COR-08). */
class DispatchJobGuardTest {

    private DispatchJob newJob() {
        return new DispatchJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void acceptFromAssignedSucceeds() {
        DispatchJob job = newJob();
        job.accept();
        assertThat(job.getStatus()).isEqualTo(DispatchJobStatus.ACCEPTED);
    }

    @Test
    void rejectFromAssignedMarksReassignable() {
        DispatchJob job = newJob();
        job.reject("Muito longe");
        assertThat(job.getStatus()).isEqualTo(DispatchJobStatus.REASSIGNABLE);
        assertThat(job.getRejectionReason()).isEqualTo("Muito longe");
    }

    @Test
    void acceptOfAlreadyAcceptedJobIsConflict() {
        DispatchJob job = newJob();
        job.accept();
        assertThatThrownBy(job::accept)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void acceptOfReassignableJobIsConflict() {
        DispatchJob job = newJob();
        job.reject("x");
        assertThatThrownBy(job::accept)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectOfAcceptedJobIsConflict() {
        DispatchJob job = newJob();
        job.accept();
        assertThatThrownBy(() -> job.reject("x"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(job.getStatus()).isEqualTo(DispatchJobStatus.ACCEPTED);
    }

    @Test
    void rejectTwiceIsConflict() {
        DispatchJob job = newJob();
        job.reject("x");
        assertThatThrownBy(() -> job.reject("y"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }
}
