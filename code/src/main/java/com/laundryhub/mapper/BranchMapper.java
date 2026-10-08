package com.laundryhub.mapper;

import com.laundryhub.domain.entity.Branch;
import com.laundryhub.dto.request.BranchRequest;
import com.laundryhub.dto.response.BranchResponse;
import org.springframework.stereotype.Component;

@Component
public class BranchMapper {

    public BranchResponse toResponse(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getName(), branch.getAddress(), branch.getPhone());
    }

    public Branch toEntity(BranchRequest request) {
        Branch branch = new Branch();
        apply(branch, request);
        return branch;
    }

    public void apply(Branch branch, BranchRequest request) {
        branch.setName(request.name());
        branch.setAddress(request.address());
        branch.setPhone(request.phone());
    }
}
