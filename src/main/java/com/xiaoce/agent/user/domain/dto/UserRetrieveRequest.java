package com.xiaoce.agent.user.domain.dto;

import com.xiaoce.agent.user.enums.RetrieveDocTypeEnums;
import com.xiaoce.agent.user.enums.RetrieveSortEnums;

import java.util.Optional;

/**
 * UserRetrieveRequest
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/27 13:15
 */
public record UserRetrieveRequest(
        Optional<Integer> yearStart,
        Optional<Integer> yearEnd,
        Optional<Integer> maxResults,
        Optional<Integer> matchScore,
        Optional<Integer> sourceFlags,
        Optional<RetrieveSortEnums> defaultSort,
        Optional<RetrieveDocTypeEnums> docType
) {
}
