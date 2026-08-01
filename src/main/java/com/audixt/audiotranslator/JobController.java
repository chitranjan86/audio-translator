package com.audixt.audiotranslator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
public class JobController {

    @Autowired
    private JobRepository jobRepository;

    // shows ALL jobs ever created
    @GetMapping("/jobs")
    public List<Job> getAllJobs() {
        return jobRepository.findAll(); // comes free from JpaRepository
    }

    // shows ONE job by its id
    @GetMapping("/jobs/{id}")
    public Job getJobById(@PathVariable Long id) {
        return jobRepository.findById(id).orElse(null);
    }
}