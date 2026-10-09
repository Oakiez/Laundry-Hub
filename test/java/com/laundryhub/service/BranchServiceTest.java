package com.laundryhub.service;

import com.laundryhub.domain.entity.Branch;
import com.laundryhub.dto.request.BranchRequest;
import com.laundryhub.dto.response.BranchResponse;
import com.laundryhub.exception.ResourceNotFoundException;
import com.laundryhub.mapper.BranchMapper;
import com.laundryhub.repository.BranchRepository;
import com.laundryhub.service.impl.BranchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    @Mock
    private BranchRepository branchRepository;

    private BranchService branchService;

    @BeforeEach
    void setUp() {
        branchService = new BranchServiceImpl(branchRepository, new BranchMapper());
    }

    private Branch branch(Long id, String name) {
        Branch b = new Branch();
        b.setId(id);
        b.setName(name);
        b.setAddress("addr");
        b.setPhone("043-000");
        return b;
    }

    @Test
    void findAll_returnsMappedBranches() {
        when(branchRepository.findAll()).thenReturn(List.of(branch(1L, "A"), branch(2L, "B")));

        List<BranchResponse> result = branchService.findAll();

        assertThat(result).extracting(BranchResponse::name).containsExactly("A", "B");
    }

    @Test
    void findById_found() {
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch(1L, "A")));

        assertThat(branchService.findById(1L).name()).isEqualTo("A");
    }

    @Test
    void findById_notFound_throws() {
        when(branchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> branchService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void create_savesAndReturnsResponse() {
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> {
            Branch b = inv.getArgument(0);
            b.setId(5L);
            return b;
        });

        BranchResponse result = branchService.create(new BranchRequest("New", "addr", "043"));

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.name()).isEqualTo("New");
    }

    @Test
    void update_changesFieldsOnExistingBranch() {
        Branch existing = branch(1L, "Old");
        when(branchRepository.findById(1L)).thenReturn(Optional.of(existing));

        BranchResponse result = branchService.update(1L, new BranchRequest("Renamed", "new addr", "043-111"));

        assertThat(result.name()).isEqualTo("Renamed");
        assertThat(existing.getAddress()).isEqualTo("new addr");
        assertThat(existing.getPhone()).isEqualTo("043-111");
    }

    @Test
    void update_notFound_throws() {
        when(branchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> branchService.update(99L, new BranchRequest("X", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesThenFlushes() {
        Branch existing = branch(1L, "A");
        when(branchRepository.findById(1L)).thenReturn(Optional.of(existing));

        branchService.delete(1L);

        InOrder order = inOrder(branchRepository);
        order.verify(branchRepository).delete(existing);
        order.verify(branchRepository).flush();
    }

    @Test
    void delete_notFound_throwsAndDeletesNothing() {
        when(branchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> branchService.delete(99L)).isInstanceOf(ResourceNotFoundException.class);

        verify(branchRepository, never()).delete(any());
    }
}
