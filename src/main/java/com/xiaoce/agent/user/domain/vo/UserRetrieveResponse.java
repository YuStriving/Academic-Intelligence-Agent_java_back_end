package com.xiaoce.agent.user.domain.vo;

import com.xiaoce.agent.user.domain.po.UserSearchDefaultConfig;
import com.xiaoce.agent.user.domain.po.UserSearchPreferences;
import com.xiaoce.agent.user.enums.RetrieveDocTypeEnums;
import com.xiaoce.agent.user.enums.RetrieveSortEnums;

/**
 * UserRetrieveRespone
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/26 19:59
 */
public record UserRetrieveResponse(
         Long  userid,

         Integer yearStart,

         Integer yearEnd,

         Integer maxResults,


         Integer matchScore,

         Integer sourceFlags,


         RetrieveSortEnums defaultSort,

         RetrieveDocTypeEnums docType
) {

    public static UserRetrieveResponse toUserRetrieveDefaultResponse(Long userid, UserSearchDefaultConfig config) {
        return new UserRetrieveResponse(
                userid,
                config.getYearStart(),
                config.getYearEnd(),
                config.getMaxResults(),
                config.getMatchScore(),
                config.getSourceFlags(),
                config.getDefaultSort(),
                config.getDocType()
        );
    }
    public static UserRetrieveResponse toUserRetrieveResponse(Long userid, UserSearchPreferences config) {
        return new UserRetrieveResponse(
                userid,
                config.getYearStart(),
                config.getYearEnd(),
                config.getMaxResults(),
                config.getMatchScore(),
                config.getSourceFlags(),
                RetrieveSortEnums.getByCode(config.getDefaultSort()),
                RetrieveDocTypeEnums.getByCode(config.getDocType())
                );
    }
}
