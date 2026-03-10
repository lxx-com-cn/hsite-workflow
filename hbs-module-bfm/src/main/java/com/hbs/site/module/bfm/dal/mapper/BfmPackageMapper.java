package com.hbs.site.module.bfm.dal.mapper;

import com.hbs.site.module.bfm.dal.entity.BfmPackage;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import static com.hbs.site.module.bfm.dal.entity.BfmPackage.*;

/**
 * 流程包Mapper
 */
@Mapper
public interface BfmPackageMapper extends BaseMapper<BfmPackage> {

    /**
     * 根据包ID和版本查询
     */
    default BfmPackage selectByPackageIdAndVersion(String packageId, String version) {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(PACKAGE_ID.eq(packageId))
                .and(VERSION.eq(version))
                .and(IS_DELETED.eq(0));
        return selectOneByQuery(wrapper);
    }

    /**
     * 查询最新版本的包
     */
    @Select("SELECT * FROM bfm_package WHERE package_id = #{packageId} " +
            "AND is_deleted = 0 ORDER BY deploy_time DESC LIMIT 1")
    BfmPackage selectLatestByPackageId(@Param("packageId") String packageId);

    /**
     * 查询所有活跃包
     */
    default List<BfmPackage> selectAllActive() {
        QueryWrapper wrapper = QueryWrapper.create()
                .where(STATUS.eq("ACTIVE"))
                .and(IS_DELETED.eq(0));
        return selectListByQuery(wrapper);
    }
}