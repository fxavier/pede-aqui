package com.delivery.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.dispatch.entity.CourierStatus;
import com.delivery.dispatch.entity.CourierVehicleType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** LAC-42: vehicle aliases (same prefix rules as the V060 data mapping) and the on-duty statuses. */
class CourierVehicleTypeTest {

    @ParameterizedTest
    @CsvSource({
            "MOTORBIKE,MOTORBIKE", "motorbike,MOTORBIKE", "Motorcycle,MOTORBIKE", "Mota,MOTORBIKE", "Moto,MOTORBIKE",
            "Motorizada,MOTORBIKE", "scooter,MOTORBIKE",
            "Bicicleta,BICYCLE", "bike,BICYCLE", "BICYCLE,BICYCLE",
            "Carro,CAR", "car,CAR", "Automóvel,CAR", "Viatura,CAR",
            "A pé,ON_FOOT", "a pe,ON_FOOT", "on foot,ON_FOOT", "ON_FOOT,ON_FOOT", "pedestre,ON_FOOT", "pé,ON_FOOT",
            "other,OTHER"
    })
    void parsesNamesAndAliases(String raw, CourierVehicleType expected) {
        assertThat(CourierVehicleType.parse(raw)).isEqualTo(expected);
    }

    @Test
    void blankIsNoVehicle() {
        assertThat(CourierVehicleType.parse(null)).isNull();
        assertThat(CourierVehicleType.parse("   ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Camião", "drone", "pedal", "xyz"})
    void unknownTextIsRejected(String raw) {
        assertThatThrownBy(() -> CourierVehicleType.parse(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAvailableAndBusyAreOnDuty() {
        assertThat(CourierStatus.AVAILABLE.isOnDuty()).isTrue();
        assertThat(CourierStatus.BUSY.isOnDuty()).isTrue();
        assertThat(CourierStatus.OFFLINE.isOnDuty()).isFalse();
        assertThat(CourierStatus.SUSPENDED.isOnDuty()).isFalse();
    }
}
