/**
 * Botaoyx.com Inc.
 * Copyright (c) 2021-2021 All Rights Reserved.
 */
package com.bt.mybatis;

import java.util.HashMap;
import java.util.Map;

import com.bt.model.PagedList;
import com.bt.model.PagedQuery;

/**
 *
 * @author Martin.C
 * @version 2021/11/01 11:16 AM
 */
public interface  PagedQueryHelper<Query> extends ToMap<Query> {



    default <DTO> PagedList<DTO> pager(PagedQuery<Query> query ,PagedSelect<DTO>  call){
        return pager(query, this,call);
    }

    static  <Query,DTO> PagedList<DTO> pager(PagedQuery<Query> query, ToMap<Query> mapper,
                                                    PagedSelect<DTO> call){
        return pager(query,mapper.to(query.getQ()),call);
    }

    static  <Query,DTO> PagedList<DTO> pager(PagedQuery<Query> query, FillMap<Query> mapper,
                                                    PagedSelect<DTO> call){
        var map = new HashMap<String,Object>();
        mapper.fill(query.getQ(),map);
        return pager(query,map,call);
    }


    static  <Query,DTO> PagedList<DTO> pager(PagedQuery<Query> query, Map<String,Object> map,
                                             PagedSelect<DTO> call){
        var bounds = DbBounds.fromPage(query.getPage(), query.getPageSize());
        var list = call.list(map, bounds);
        //HACK , this call rewrite count to bounds
        return new PagedList<>(bounds.getCount(), list);
    }

}