package com.brand.agentpoc.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResultTest {

    @Test
    void successCreatesResultWithCode200() {
        ApiResult<String> result = ApiResult.success("hello");

        assertThat(result.code()).isEqualTo(200);
        assertThat(result.data()).isEqualTo("hello");
        assertThat(result.message()).isEqualTo("success");
    }

    @Test
    void errorCreatesResultWithGivenCodeAndMessage() {
        ApiResult<Void> result = ApiResult.error(401, "Invalid API key");

        assertThat(result.code()).isEqualTo(401);
        assertThat(result.data()).isNull();
        assertThat(result.message()).isEqualTo("Invalid API key");
    }

    @Test
    void successWithNullDataReturnsNullData() {
        ApiResult<Void> result = ApiResult.success(null);

        assertThat(result.code()).isEqualTo(200);
        assertThat(result.data()).isNull();
    }
}
