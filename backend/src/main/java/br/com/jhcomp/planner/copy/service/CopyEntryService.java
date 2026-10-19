package br.com.jhcomp.planner.copy.service;

import br.com.jhcomp.planner.common.NotFoundException;
import br.com.jhcomp.planner.copy.domain.CopyEntry;
import br.com.jhcomp.planner.copy.repository.CopyEntryRepository;
import br.com.jhcomp.planner.copy.web.dto.CopyDtos.CopyRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CopyEntryService {

    private final CopyEntryRepository repository;

    public CopyEntryService(CopyEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CopyEntry> list(String tone, String postCode) {
        if (postCode != null && !postCode.isBlank()) {
            return repository.findByPostCodeOrderByIdAsc(postCode);
        }
        if (tone != null && !tone.isBlank()) {
            return repository.findByToneOrderByPostCodeAsc(tone);
        }
        return repository.findAllByOrderByPostCodeAsc();
    }

    @Transactional(readOnly = true)
    public CopyEntry getById(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Copy", id));
    }

    public CopyEntry create(CopyRequest request) {
        CopyEntry entry = new CopyEntry(request.headline(), request.body());
        apply(entry, request);
        return repository.save(entry);
    }

    public CopyEntry update(Long id, CopyRequest request) {
        CopyEntry entry = getById(id);
        entry.setHeadline(request.headline());
        entry.setBody(request.body());
        apply(entry, request);
        return repository.save(entry);
    }

    public void delete(Long id) {
        repository.delete(getById(id));
    }

    private void apply(CopyEntry entry, CopyRequest request) {
        entry.setPostCode(request.postCode());
        entry.setTone(request.tone() == null || request.tone().isBlank() ? "AUTORIDADE" : request.tone());
        entry.setHashtags(request.hashtags());
        entry.setCta(request.cta());
        entry.setStrategy(request.strategy());
        entry.setBestTime(request.bestTime());
    }
}
