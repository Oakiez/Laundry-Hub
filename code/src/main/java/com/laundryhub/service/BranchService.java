package com.laundryhub.service;

import com.laundryhub.dto.request.BranchRequest;
import com.laundryhub.dto.response.BranchResponse;

import java.util.List;

public interface BranchService {

    List<BranchResponse> findAll();

    BranchResponse findById(Long id);

    BranchResponse create(BranchRequest request);

    BranchResponse update(Long id, BranchRequest request);

    void delete(Long id);
}
