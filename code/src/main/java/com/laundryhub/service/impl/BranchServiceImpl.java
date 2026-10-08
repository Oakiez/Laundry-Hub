package com.laundryhub.service.impl;

import com.laundryhub.domain.entity.Branch;
import com.laundryhub.dto.request.BranchRequest;
import com.laundryhub.dto.response.BranchResponse;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.mapper.BranchMapper;
import com.laundryhub.repository.BranchRepository;
import com.laundryhub.service.BranchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;

    public BranchServiceImpl(BranchRepository branchRepository, BranchMapper branchMapper) {
        this.branchRepository = branchRepository;
        this.branchMapper = branchMapper;
    }

    @Override
    public List<BranchResponse> findAll() {
        return branchRepository.findAll().stream().map(branchMapper::toResponse).toList();
    }

    @Override
    public BranchResponse findById(Long id) {
        return branchMapper.toResponse(getBranch(id));
    }

    @Override
    @Transactional
    public BranchResponse create(BranchRequest request) {
        return branchMapper.toResponse(branchRepository.save(branchMapper.toEntity(request)));
    }

    @Override
    @Transactional
    public BranchResponse update(Long id, BranchRequest request) {
        Branch branch = getBranch(id);
        branchMapper.apply(branch, request);
        return branchMapper.toResponse(branch);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Branch branch = getBranch(id);
        branchRepository.delete(branch);
        // flush now so a branch still used by machines/orders fails here (-> 409), not later at commit time
        branchRepository.flush();
    }

    private Branch getBranch(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch " + id + " not found"));
    }
}
